package com.pedidos360.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.orders.repository.*;
import com.pedidos360.orders.service.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired OrderRepository orders;
  @Autowired AuditRepository audit;
  @Autowired OutboxRepository outbox;
  @Autowired OperatorRepository operators;
  @Autowired InventoryRepository inventory;
  @Autowired InventoryRecovery recovery;
  @MockitoBean CatalogClient catalog;
  @Autowired org.springframework.transaction.PlatformTransactionManager txManager;
  static final String ORDER =
      """
      {"storeId":1,"customerName":"Ana","customerEmail":"ana@example.test","items":[{"productId":10,"quantity":2,"unitPrice":1},{"productId":11,"quantity":1,"unitPrice":1}]}
      """;

  @BeforeEach
  void setup() {
    inventory.deleteAll();
    outbox.deleteAll();
    audit.deleteAll();
    orders.deleteAll();
    operators.deleteAll();
    when(catalog.product(10L))
        .thenReturn(new CatalogClient.Product(10L, 1L, new BigDecimal("2200"), true, 40));
    when(catalog.product(11L))
        .thenReturn(new CatalogClient.Product(11L, 1L, new BigDecimal("2500"), true, 100));
  }

  static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(
      String id, String role) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(j -> j.claim("tid", "tenant").claim("oid", id))
        .authorities(
            new SimpleGrantedAuthority("SCOPE_access_as_user"),
            new SimpleGrantedAuthority("ROLE_" + role));
  }

  long create() throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/orders")
                        .with(actor("ana", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(6900))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("id")
        .asLong();
  }

  @Test
  void noToken401() throws Exception {
    mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void noRole403() throws Exception {
    mvc.perform(get("/api/orders").with(SecurityMockMvcRequestPostProcessors.jwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void identityAndServerPricesOutboxAudit() throws Exception {
    long id = create();
    assertEquals("tenant:ana", orders.findById(id).orElseThrow().getOwnerId());
    assertEquals(1, outbox.count());
    assertEquals(1, audit.count());
  }

  @Test
  void creationAboveStockDoesNotCreateOrderAuditOrMessage() throws Exception {
    var request = json.readTree(ORDER);
    ((com.fasterxml.jackson.databind.node.ObjectNode) request.path("items").get(0))
        .put("quantity", 41);
    mvc.perform(post("/api/orders").with(actor("ana", "Cliente"))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("disponibles 40, solicitados 41")));
    assertEquals(0, orders.count());
    assertEquals(0, audit.count());
    assertEquals(0, outbox.count());
    verify(catalog, never()).reserve(any(), anyString());
  }

  @Test
  void duplicateProductLinesShareTheSameAvailableStock() throws Exception {
    var request = json.readTree(ORDER);
    var lines = ((com.fasterxml.jackson.databind.node.ObjectNode) request).putArray("items");
    lines.addObject().put("productId", 10).put("quantity", 25);
    lines.addObject().put("productId", 10).put("quantity", 20);
    mvc.perform(post("/api/orders").with(actor("ana", "Cliente"))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("solicitados 45")));
    assertEquals(0, orders.count());
    assertEquals(0, outbox.count());
    verify(catalog, times(1)).product(10L);
  }

  @Test
  void duplicateProductLinesAtTheStockLimitAreAllowed() throws Exception {
    var request = json.readTree(ORDER);
    var lines = ((com.fasterxml.jackson.databind.node.ObjectNode) request).putArray("items");
    lines.addObject().put("productId", 10).put("quantity", 25);
    lines.addObject().put("productId", 10).put("quantity", 15);
    mvc.perform(post("/api/orders").with(actor("ana", "Cliente"))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.total").value(88000));
    assertEquals(1, orders.count());
    assertEquals(1, outbox.count());
    verify(catalog, times(1)).product(10L);
  }

  @Test
  void unavailableOrUnknownStockCannotCreateOrders() throws Exception {
    for (Integer stock : new Integer[] {0, null}) {
      when(catalog.product(10L))
          .thenReturn(new CatalogClient.Product(10L, 1L, new BigDecimal("2200"), true, stock));
      mvc.perform(post("/api/orders").with(actor("ana", "Cliente"))
              .contentType(MediaType.APPLICATION_JSON).content(ORDER))
          .andExpect(stock == null ? status().isServiceUnavailable() : status().isConflict());
    }
    assertEquals(0, orders.count());
    assertEquals(0, audit.count());
    assertEquals(0, outbox.count());
  }

  @Test
  void editAboveCurrentStockPreservesTheSavedOrderAndOutbox() throws Exception {
    long id = create();
    // Another accepted order can reduce stock since this order was created.
    when(catalog.product(10L))
        .thenReturn(new CatalogClient.Product(10L, 1L, new BigDecimal("2200"), true, 1));
    mvc.perform(put("/api/orders/" + id).with(actor("ana", "Cliente"))
            .contentType(MediaType.APPLICATION_JSON).content(ORDER))
        .andExpect(status().isConflict());
    var saved = orders.findById(id).orElseThrow();
    assertEquals(0, saved.getTotal().compareTo(new BigDecimal("6900")));
    assertEquals(1, audit.count());
    assertEquals(1, outbox.count());
  }

  @Test
  void cannotReadOrEditAnotherCustomer() throws Exception {
    long id = create();
    mvc.perform(get("/api/orders/" + id).with(actor("otro", "Cliente")))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/orders/" + id)
                .with(actor("otro", "Cliente"))
                .contentType("application/json")
                .content(ORDER))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/orders").with(actor("otro", "Cliente")))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void operatorNeedsDatabaseAssignment() throws Exception {
    long id = create();
    mvc.perform(get("/api/orders/" + id).with(actor("op", "Operador")))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/operators")
                .with(actor("admin", "Admin"))
                .contentType("application/json")
                .content("{\"actorId\":\"tenant:op\",\"storeId\":1}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/orders/" + id).with(actor("op", "Operador"))).andExpect(status().isOk());
  }

  @Test
  void lifecycleAndDuplicateAcceptance() throws Exception {
    long id = create();
    move(id, "DESPACHADO", "Admin").andExpect(status().isConflict());
    move(id, "ACEPTADO", "Cliente").andExpect(status().isForbidden());
    move(id, "ACEPTADO", "Admin").andExpect(status().isOk());
    move(id, "ACEPTADO", "Admin").andExpect(status().isOk());
    verify(catalog, times(1)).reserve(any(), anyString());
    assertEquals(3, outbox.count());
    mvc.perform(
            put("/api/orders/" + id)
                .with(actor("ana", "Cliente"))
                .contentType("application/json")
                .content(ORDER))
        .andExpect(status().isConflict());
    move(id, "EN_PREPARACION", "Admin").andExpect(status().isOk());
    move(id, "CANCELADO", "Admin").andExpect(status().isConflict());
    move(id, "DESPACHADO", "Admin").andExpect(status().isOk());
    move(id, "ENTREGADO", "Admin").andExpect(status().isOk());
    assertEquals(
        1, outbox.findAll().stream().filter(e -> e.routingKey.equals("invoice.gen")).count());
  }

  @Test
  void customerCancellationAndRecovery() throws Exception {
    long id = create();
    move(id, "CANCELADO", "Cliente").andExpect(status().isOk());
    recovery.reconcile();
    verify(catalog, never()).release(anyString());
    assertEquals(0, inventory.count());
  }

  @Test
  void failedReservationRetainsRecoveryRecord() throws Exception {
    long id = create();
    doThrow(new org.springframework.web.client.ResourceAccessException("offline"))
        .when(catalog)
        .reserve(any(), anyString());
    move(id, "ACEPTADO", "Admin").andExpect(status().isServiceUnavailable());
    assertEquals("CREADO", orders.findById(id).orElseThrow().getStatus().name());
    assertFalse(inventory.findByOrderId(id).getFirst().completed);
    recovery.reconcile();
    verify(catalog).release(anyString());
    assertTrue(inventory.findByOrderId(id).getFirst().completed);
  }

  @Test
  void auditorReadOnlyHistory() throws Exception {
    create();
    mvc.perform(get("/api/audit").with(actor("audit", "Auditor")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].actorId").value("tenant:ana"));
    mvc.perform(get("/api/orders").with(actor("audit", "Auditor")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/outbox").with(actor("audit", "Auditor")))
        .andExpect(status().isForbidden());
  }

  @Test
  void deletionRestrictedAndHistoryPreserved() throws Exception {
    long id = create();
    mvc.perform(delete("/api/orders/" + id).with(actor("ana", "Cliente")))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/orders/" + id).with(actor("admin", "Admin")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/orders/" + id).with(actor("admin", "Admin")))
        .andExpect(status().isNotFound());
    assertEquals(2, audit.count());
  }

  @Test
  void invalidItemsStoreAndDates400() throws Exception {
    mvc.perform(
            post("/api/orders")
                .with(actor("ana", "Cliente"))
                .contentType("application/json")
                .content(
                    ORDER.replace(
                        "[{\"productId\":10,\"quantity\":2,\"unitPrice\":1},{\"productId\":11,\"quantity\":1,\"unitPrice\":1}]",
                        "[null]")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/orders?from=2026-10-08T00:00:00&to=2026-10-07T00:00:00")
                .with(actor("admin", "Admin")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/orders?size=9999").with(actor("admin", "Admin")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reportUsesDeliveredSalesOnly() throws Exception {
    create();
    mvc.perform(get("/api/reports").with(actor("admin", "Admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sales").value(0));
    mvc.perform(get("/api/reports").with(actor("ana", "Cliente")))
        .andExpect(status().isForbidden());
  }

  @Test
  void successiveFailuresKeepSeparateRecoveryIntents() throws Exception {
    long id = create();
    doThrow(new org.springframework.web.client.ResourceAccessException("offline"))
        .doThrow(new org.springframework.web.client.ResourceAccessException("offline"))
        .doNothing()
        .when(catalog)
        .reserve(any(), anyString());
    move(id, "ACEPTADO", "Admin").andExpect(status().isServiceUnavailable());
    move(id, "ACEPTADO", "Admin").andExpect(status().isServiceUnavailable());
    move(id, "ACEPTADO", "Admin").andExpect(status().isOk());
    assertEquals(3, inventory.count());
    String committed = orders.findById(id).orElseThrow().getReservationId();
    recovery.reconcile();
    verify(catalog, times(2)).release(anyString());
    verify(catalog, never()).release(committed);
    assertTrue(inventory.findAll().stream().allMatch(o -> o.completed));
  }

  @Test
  void cancelledAcceptanceReleasesCommittedReservation() throws Exception {
    long id = create();
    move(id, "ACEPTADO", "Admin").andExpect(status().isOk());
    String committed = orders.findById(id).orElseThrow().getReservationId();
    move(id, "CANCELADO", "Admin").andExpect(status().isOk());
    recovery.reconcile();
    verify(catalog).release(committed);
    assertTrue(inventory.findById(committed).orElseThrow().completed);
  }

  @Test
  void outboxPublishedOnlyAfterBrokerConfirmation() throws Exception {
    create();
    var rabbit = mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
    doAnswer(
            call -> {
              org.springframework.amqp.rabbit.connection.CorrelationData c = call.getArgument(3);
              c.getFuture()
                  .complete(
                      new org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(
                          true, null));
              return null;
            })
        .when(rabbit)
        .send(
            eq("cmd.direct"),
            anyString(),
            any(org.springframework.amqp.core.Message.class),
            any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    new OutboxPublisher(
            outbox,
            rabbit,
            new org.springframework.transaction.support.TransactionTemplate(txManager))
        .publish();
    assertNotNull(outbox.findAll().getFirst().publishedAt);
    assertEquals(1, outbox.findAll().getFirst().attempts);
  }

  @Test
  void unroutableMessageStaysPendingDespiteAck() throws Exception {
    create();
    var rabbit = mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
    doAnswer(
            call -> {
              org.springframework.amqp.rabbit.connection.CorrelationData c = call.getArgument(3);
              c.setReturned(
                  new org.springframework.amqp.core.ReturnedMessage(
                      call.getArgument(2), 312, "NO_ROUTE", "cmd.direct", "email.send"));
              c.getFuture()
                  .complete(
                      new org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(
                          true, null));
              return null;
            })
        .when(rabbit)
        .send(
            eq("cmd.direct"),
            anyString(),
            any(org.springframework.amqp.core.Message.class),
            any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    new OutboxPublisher(
            outbox,
            rabbit,
            new org.springframework.transaction.support.TransactionTemplate(txManager))
        .publish();
    var entry = outbox.findAll().getFirst();
    assertNull(entry.publishedAt);
    assertNotNull(entry.lastError);
    assertTrue(entry.nextAttempt.isAfter(entry.createdAt));
  }

  org.springframework.test.web.servlet.ResultActions move(long id, String status, String role)
      throws Exception {
    return mvc.perform(
        put("/api/orders/" + id + "/status")
            .with(actor(role.equals("Cliente") ? "ana" : "admin", role))
            .contentType("application/json")
            .content("{\"status\":\"" + status + "\"}"));
  }
}
