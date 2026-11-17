package com.pedidos360.audit;
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
class AuditProjectionTest {
  @Autowired AuditProcessor processor;
  @Autowired AuditRepository repository;
  @Autowired MockMvc mvc;
  @BeforeEach void clean() { repository.deleteAll(); }
  static BusinessEvent event(long version) {
    return new BusinessEvent(UUID.randomUUID().toString(),
        version == 1 ? "OrderCreated" : "OrderUpdated", version == 1 ? "CREATED" : "UPDATED",
        Instant.parse("2026-10-01T12:00:00Z").plusSeconds(version), UUID.randomUUID().toString(),
        "tenant:actor", "ms-pedidos360-orders", null,
        new BusinessEvent.Snapshot(1L, version, 1L, "CREADO", false, new BigDecimal("4400"),
            Instant.parse("2026-10-01T12:00:00Z"), null,
            List.of(new BusinessEvent.Line(10L, 2, new BigDecimal("2200")))));
  }
  static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(String role) {
    return SecurityMockMvcRequestPostProcessors.jwt().authorities(
        new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + role));
  }
  @Test void ingressQueuesTimelineAndOnlyTimelineConsumptionMakesItVisible() throws Exception {
    var event = event(1);
    processor.ingest(event); processor.ingest(event);
    assertEquals(1, repository.count());
    assertNull(repository.findById(event.eventId()).orElseThrow().publishedAt);
    mvc.perform(get("/api/audit").with(actor("Auditor")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    processor.project(event); processor.project(event);
    mvc.perform(get("/api/audit?orderId=1&actorId=tenant:actor").with(actor("Auditor")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].source").value("ms-pedidos360-orders"));
  }
  @Test void multipleEventsForOneOrderAreAllPreservedAndReplayDoesNotLoop() throws Exception {
    processor.project(event(1)); processor.project(event(2));
    assertEquals(2, repository.count());
    assertTrue(repository.findAll().stream().allMatch(r -> r.publishedAt != null));
    mvc.perform(get("/api/audit?type=OrderUpdated").with(actor("Admin")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  }
  @Test void reusedEventIdWithAnotherPayloadIsRejected() {
    var event = event(1); processor.ingest(event);
    var different = event(2);
    var forged = new BusinessEvent(event.eventId(), different.type(), different.action(), different.timestamp(),
        different.correlationId(), different.actorId(), different.source(), null, different.payload());
    assertThrows(IllegalArgumentException.class, () -> processor.ingest(forged));
    assertEquals(1, repository.count());
  }
  @Test void timelineIsReadOnlyAndRestrictedToAuditRoles() throws Exception {
    mvc.perform(get("/api/audit")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/audit").with(actor("Cliente"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/audit?size=501").with(actor("Admin"))).andExpect(status().isBadRequest());
    mvc.perform(get("/api/audit?from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z")
        .with(actor("Admin"))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/audit").with(actor("Auditor"))).andExpect(status().isMethodNotAllowed());
  }
}

