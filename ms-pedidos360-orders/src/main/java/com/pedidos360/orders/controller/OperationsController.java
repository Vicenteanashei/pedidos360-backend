package com.pedidos360.orders.controller;

import com.pedidos360.orders.model.*;
import com.pedidos360.orders.repository.*;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@org.springframework.validation.annotation.Validated
public class OperationsController {
  private final AuditRepository audit;
  private final OperatorRepository operators;
  private final OutboxRepository outbox;
  private final InventoryRepository inventory;

  public OperationsController(
      AuditRepository a, OperatorRepository o, OutboxRepository b, InventoryRepository i) {
    audit = a;
    operators = o;
    outbox = b;
    inventory = i;
  }

  @GetMapping("/audit")
  @PreAuthorize("hasAnyRole('Admin','Auditor')")
  public List<AuditEntry> audit(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
    return audit
        .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")))
        .getContent();
  }

  public record Assignment(
      @NotBlank @Size(max = 250) String actorId, @NotNull @Min(1) @Max(20) Long storeId) {}

  @GetMapping("/operators")
  @PreAuthorize("hasRole('Admin')")
  public List<OperatorAssignment> assignments() {
    return operators.findAll();
  }

  @PutMapping("/operators")
  @PreAuthorize("hasRole('Admin')")
  public OperatorAssignment assign(@Valid @RequestBody Assignment r) {
    var a = new OperatorAssignment();
    a.actorId = r.actorId();
    a.storeId = r.storeId();
    return operators.save(a);
  }

  @DeleteMapping("/operators/{actorId}")
  @PreAuthorize("hasRole('Admin')")
  public void revoke(@PathVariable String actorId) {
    operators.deleteById(actorId);
  }

  @GetMapping("/outbox")
  @PreAuthorize("hasRole('Admin')")
  public List<OutboxEntry> outbox() {
    return outbox
        .findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
        .getContent();
  }

  @org.springframework.transaction.annotation.Transactional
  @PostMapping("/outbox/{id}/retry")
  @PreAuthorize("hasRole('Admin')")
  public OutboxEntry retry(@PathVariable String id) {
    var e =
        outbox
            .lock(id)
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    if (e.publishedAt != null)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Ya publicado");
    e.attempts = 0;
    e.nextAttempt = java.time.Instant.now();
    return outbox.save(e);
  }

  @GetMapping("/inventory-operations")
  @PreAuthorize("hasRole('Admin')")
  public List<InventoryOperation> inventory() {
    return inventory.findAll(PageRequest.of(0, 100)).getContent();
  }
}
