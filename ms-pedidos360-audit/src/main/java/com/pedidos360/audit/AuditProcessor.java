package com.pedidos360.audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.stream.BusinessEvent;
import jakarta.validation.Validator;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditProcessor {
  private final AuditRepository records;
  private final Validator validator;
  private final ObjectMapper json;
  public AuditProcessor(AuditRepository records, Validator validator, ObjectMapper json) {
    this.records = records; this.validator = validator; this.json = json;
  }
  private AuditRecord prepare(BusinessEvent event) {
    var row = new AuditRecord();
    row.id = event.eventId(); row.orderId = event.payload().id();
    row.eventVersion = event.payload().version(); row.storeId = event.payload().storeId();
    row.actorId = event.actorId(); row.type = event.type(); row.action = event.action();
    row.status = event.payload().status(); row.previousStatus = event.previousStatus();
    row.occurredAt = event.timestamp(); row.correlationId = event.correlationId(); row.source = event.source();
    row.nextAttempt = Instant.now();
    try { row.payload = json.writeValueAsString(event); }
    catch (Exception ex) { throw new IllegalArgumentException("Evento no serializable", ex); }
    return row;
  }
  private void sameEvent(AuditRecord row, BusinessEvent event) {
    try {
      if (!json.readValue(row.payload, BusinessEvent.class).equals(event))
        throw new IllegalArgumentException("eventId reutilizado con otro contenido");
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Evento persistido inválido", ex);
    }
  }
  @Transactional
  public void ingest(BusinessEvent event) {
    event.validate(validator);
    var existing = records.lock(event.eventId());
    if (existing.isPresent()) { sameEvent(existing.get(), event); return; }
    records.saveAndFlush(prepare(event));
  }
  @Transactional
  public void project(BusinessEvent event) {
    event.validate(validator);
    var existing = records.lock(event.eventId());
    var row = existing.orElseGet(() -> prepare(event));
    if (existing.isPresent()) sameEvent(row, event);
    if (row.projectedAt != null) return;
    row.projectedAt = Instant.now();
    // Rebuilding a fresh audit DB from audit.timeline must not publish a loop.
    if (row.publishedAt == null) row.publishedAt = Instant.now();
    records.saveAndFlush(row);
  }
}

