package com.pedidos360.orders.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.common.Actor;
import com.pedidos360.orders.model.*;
import com.pedidos360.orders.repository.StreamOutboxRepository;
import com.pedidos360.stream.BusinessEvent;
import java.time.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class OrderEvents {
  private final StreamOutboxRepository outbox;
  private final ObjectMapper json;
  public OrderEvents(StreamOutboxRepository outbox, ObjectMapper json) {
    this.outbox = outbox; this.json = json;
  }

  // Joins the order transaction. This is separate from Rabbit's command outbox.
  public void record(CustomerOrder order, String action, OrderStatus previous) {
    order.setStreamVersion(order.getStreamVersion() + 1);
    String id = UUID.randomUUID().toString();
    String type = switch (action) {
      case "CREATED" -> "OrderCreated";
      case "UPDATED" -> "OrderUpdated";
      case "DELETED" -> "OrderDeleted";
      case "SNAPSHOT" -> "OrderSnapshot";
      default -> switch (order.getStatus()) {
        case ACEPTADO -> "OrderAccepted";
        case EN_PREPARACION -> "OrderPreparing";
        case DESPACHADO -> "OrderDispatched";
        case ENTREGADO -> "OrderDelivered";
        case CANCELADO -> "OrderCancelled";
        default -> throw new IllegalStateException("Estado sin evento");
      };
    };
    Instant now = Instant.now();
    var snapshot = new BusinessEvent.Snapshot(order.getId(), order.getStreamVersion(),
        order.getStoreId(), order.getStatus().name(), order.getDeleted(), order.getTotal(),
        order.getCreatedAt().toInstant(ZoneOffset.UTC),
        order.getDeliveredAt() == null ? null : order.getDeliveredAt().toInstant(ZoneOffset.UTC),
        order.getItems().stream().map(i ->
            new BusinessEvent.Line(i.getProductId(), i.getQuantity(), i.getUnitPrice())).toList());
    var event = new BusinessEvent(id, type, action, now,
        Optional.ofNullable(MDC.get("correlationId")).orElse(id), Actor.id(),
        "ms-pedidos360-orders", previous == null ? null : previous.name(), snapshot);
    var entry = new StreamOutbox();
    entry.id = id; entry.orderId = order.getId(); entry.eventVersion = order.getStreamVersion();
    entry.createdAt = now; entry.nextAttempt = now;
    try { entry.payload = json.writeValueAsString(event); }
    catch (Exception ex) { throw new IllegalStateException("No se pudo preparar el evento", ex); }
    outbox.save(entry);
  }
}

