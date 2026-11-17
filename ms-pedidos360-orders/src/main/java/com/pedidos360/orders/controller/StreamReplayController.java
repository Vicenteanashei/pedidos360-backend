package com.pedidos360.orders.controller;
import com.pedidos360.orders.repository.*;
import com.pedidos360.orders.service.OrderEvents;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/outbox/stream")
@PreAuthorize("hasRole('Admin')")
@Validated
public class StreamReplayController {
  private final OrderRepository orders;
  private final StreamOutboxRepository outbox;
  private final OrderEvents events;
  public StreamReplayController(OrderRepository orders, StreamOutboxRepository outbox, OrderEvents events) {
    this.orders = orders; this.outbox = outbox; this.events = events;
  }
  @GetMapping
  public Map<String, Object> state() {
    return Map.of("total", outbox.count(), "pending", outbox.countByPublishedAtIsNull());
  }
  @PostMapping("/replay")
  @Transactional
  public Map<String, Object> replay(@RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
    var batch = orders.findAll(PageRequest.of(page, size, Sort.by("id")));
    for (var candidate : batch) {
      var order = orders.lock(candidate.getId()).orElseThrow();
      events.record(order, "SNAPSHOT", null);
    }
    orders.flush();
    return Map.of("page", page, "queued", batch.getNumberOfElements(), "hasNext", batch.hasNext());
  }
}

