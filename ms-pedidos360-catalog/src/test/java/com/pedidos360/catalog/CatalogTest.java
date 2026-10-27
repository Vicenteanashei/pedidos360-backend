package com.pedidos360.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogTest {
  @Autowired MockMvc mvc;
  @Autowired ProductRepository products;
  @Autowired ReservationRepository reservations;
  long id;
  static final String KEY = "test-service-key-0123456789abcdef0123456789";

  @BeforeEach
  void init() {
    reservations.deleteAll();
    products.deleteAll();
    var p = new Product();
    p.name = "Pan";
    p.category = "Panadería";
    p.price = new BigDecimal("2200");
    p.storeId = 1L;
    p.stock = 5;
    id = products.save(p).id;
  }

  String payload(int quantity) {
    return "{\"storeId\":1,\"items\":[{\"productId\":" + id + ",\"quantity\":" + quantity + "}]}";
  }

  @Test
  void reserveIdempotentReleaseIdempotent() throws Exception {
    for (int i = 0; i < 2; i++)
      mvc.perform(
              put("/api/catalog/reservations/order-1")
                  .header("X-Service-Key", KEY)
                  .contentType("application/json")
                  .content(payload(3)))
          .andExpect(status().isOk());
    assertEquals(2, products.findById(id).orElseThrow().stock);
    for (int i = 0; i < 2; i++)
      mvc.perform(delete("/api/catalog/reservations/order-1").header("X-Service-Key", KEY))
          .andExpect(status().isOk());
    assertEquals(5, products.findById(id).orElseThrow().stock);
  }

  @Test
  void insufficientStockRollsBack() throws Exception {
    mvc.perform(
            put("/api/catalog/reservations/order-1")
                .header("X-Service-Key", KEY)
                .contentType("application/json")
                .content(payload(6)))
        .andExpect(status().isConflict());
    assertEquals(5, products.findById(id).orElseThrow().stock);
    assertEquals(0, reservations.count());
  }

  @Test
  void concurrentReservationsNeverOversell() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () ->
                  mvc.perform(
                          put("/api/catalog/reservations/order-1")
                              .header("X-Service-Key", KEY)
                              .contentType("application/json")
                              .content(payload(4)))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      var b =
          pool.submit(
              () ->
                  mvc.perform(
                          put("/api/catalog/reservations/order-2")
                              .header("X-Service-Key", KEY)
                              .contentType("application/json")
                              .content(payload(4)))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      var statuses = java.util.List.of(a.get(), b.get());
      assertTrue(statuses.contains(200));
      assertTrue(statuses.contains(409));
      assertEquals(1, products.findById(id).orElseThrow().stock);
    }
  }

  @Test
  void bearerCannotBypassInternalCredential() throws Exception {
    mvc.perform(
            put("/api/catalog/reservations/order-1")
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority("SCOPE_access_as_user"),
                            new SimpleGrantedAuthority("ROLE_Admin")))
                .contentType("application/json")
                .content(payload(1)))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotWriteProduct() throws Exception {
    mvc.perform(
            delete("/api/catalog/products/" + id)
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority("SCOPE_access_as_user"),
                            new SimpleGrantedAuthority("ROLE_Cliente"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void conflictingReservationRejected() throws Exception {
    mvc.perform(
            put("/api/catalog/reservations/order-1")
                .header("X-Service-Key", KEY)
                .contentType("application/json")
                .content(payload(1)))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/catalog/reservations/order-1")
                .header("X-Service-Key", KEY)
                .contentType("application/json")
                .content(payload(2)))
        .andExpect(status().isConflict());
  }

  @Test
  void releaseBeforeDelayedReserveBlocksStaleAttempt() throws Exception {
    mvc.perform(delete("/api/catalog/reservations/order-99").header("X-Service-Key", KEY))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/catalog/reservations/order-99")
                .header("X-Service-Key", KEY)
                .contentType("application/json")
                .content(payload(3)))
        .andExpect(status().isConflict());
    assertEquals(5, products.findById(id).orElseThrow().stock);
  }
}
