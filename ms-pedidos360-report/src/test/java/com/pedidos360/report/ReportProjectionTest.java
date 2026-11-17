package com.pedidos360.report;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.pedidos360.stream.BusinessEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReportProjectionTest {
  @Autowired ReportProcessor processor;
  @Autowired ReportRepository repository;
  @Autowired MockMvc mvc;
  @BeforeEach void clean() { repository.deleteAll(); }
  static BusinessEvent event(long id, long version, String status, String type, String action) {
    Instant created = Instant.parse("2026-10-01T12:00:00Z");
    return new BusinessEvent(UUID.randomUUID().toString(), type, action, created.plusSeconds(version),
        UUID.randomUUID().toString(), "tenant:actor", "ms-pedidos360-orders", null,
        new BusinessEvent.Snapshot(id, version, 1L, status, action.equals("DELETED"), new BigDecimal("4400"),
            created, status.equals("ENTREGADO") ? created.plusSeconds(3600) : null,
            List.of(new BusinessEvent.Line(10L, 2, new BigDecimal("2200")))));
  }
  static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(String role) {
    return SecurityMockMvcRequestPostProcessors.jwt().authorities(
        new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + role));
  }
  @Test void snapshotsAreIdempotentAndOlderEventsCannotUndoDeliveredSales() throws Exception {
    var delivered = event(1, 5, "ENTREGADO", "OrderDelivered", "STATUS_CHANGED");
    processor.process(delivered); processor.process(delivered);
    processor.process(event(1, 1, "CREADO", "OrderCreated", "CREATED"));
    assertEquals(1, repository.count());
    assertEquals(5, repository.findById(1L).orElseThrow().eventVersion);
    mvc.perform(get("/api/reports?range=all").with(actor("Admin")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.deliveredOrders").value(1))
        .andExpect(jsonPath("$.sales").value(4400))
        .andExpect(jsonPath("$.averageLeadTimeMs").value(3600000))
        .andExpect(jsonPath("$.topProducts[0].value").value(2))
        .andExpect(jsonPath("$.streaming").value(true));
  }
  @Test void invalidPayloadDoesNotCreateAProjection() {
    var valid = event(1, 1, "CREADO", "OrderCreated", "CREATED");
    var bad = new BusinessEvent(valid.eventId(), valid.type(), valid.action(), valid.timestamp(),
        valid.correlationId(), valid.actorId(), valid.source(), null,
        new BusinessEvent.Snapshot(1L, 1, 1L, "CREADO", false, BigDecimal.ZERO,
            valid.payload().createdAt(), null, valid.payload().items()));
    assertThrows(IllegalArgumentException.class, () -> processor.process(bad));
    assertEquals(0, repository.count());
  }
  @Test void deletedSnapshotRemovesOrderFromActiveCounts() throws Exception {
    processor.process(event(1, 1, "CREADO", "OrderCreated", "CREATED"));
    processor.process(event(1, 2, "CREADO", "OrderDeleted", "DELETED"));
    mvc.perform(get("/api/report/kpis?range=all").with(actor("Admin")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.states").isEmpty());
  }
  @Test void reportRequiresAdminAndRejectsInvalidRanges() throws Exception {
    mvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
    for (String role : List.of("Cliente", "Operador", "Auditor"))
      mvc.perform(get("/api/reports").with(actor(role))).andExpect(status().isForbidden());
    mvc.perform(get("/api/reports?range=invalid").with(actor("Admin"))).andExpect(status().isBadRequest());
  }
}

