package com.pedidos360.audit;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('Admin','Auditor')")
@Validated
public class TimelineController {
  private final AuditRepository records;
  public TimelineController(AuditRepository records) { this.records = records; }
  public record Row(String id, String eventId, Long orderId, Long storeId, String actorId,
      String action, String type, String previousStatus, String status, Instant occurredAt,
      String correlationId, String source, long eventVersion) {}

  @GetMapping({"", "/timeline"})
  public List<Row> list(@RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size,
      @RequestParam(required = false) @Positive Long orderId,
      @RequestParam(required = false) @Size(max = 250) String actorId,
      @RequestParam(required = false) @Size(max = 40) String type,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    if (from != null && to != null && from.isAfter(to))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Desde debe ser anterior a hasta");
    Specification<AuditRecord> spec = (root, query, cb) -> {
      var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
      filters.add(cb.isNotNull(root.get("projectedAt")));
      if (orderId != null) filters.add(cb.equal(root.get("orderId"), orderId));
      if (actorId != null && !actorId.isBlank()) filters.add(cb.equal(root.get("actorId"), actorId));
      if (type != null && !type.isBlank()) filters.add(cb.equal(root.get("type"), type));
      if (from != null) filters.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
      if (to != null) filters.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
      return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
    return records.findAll(spec, PageRequest.of(page, size,
        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")))).stream()
        .map(r -> new Row(r.id, r.id, r.orderId, r.storeId, r.actorId, r.action, r.type,
            r.previousStatus, r.status, r.occurredAt, r.correlationId, r.source, r.eventVersion)).toList();
  }
}

