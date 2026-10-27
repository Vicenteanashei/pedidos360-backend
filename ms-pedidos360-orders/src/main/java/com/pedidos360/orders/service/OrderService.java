package com.pedidos360.orders.service;

import static com.pedidos360.common.MessagingConfig.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.common.Actor;
import com.pedidos360.orders.dto.*;
import com.pedidos360.orders.exception.*;
import com.pedidos360.orders.model.*;
import com.pedidos360.orders.repository.*;
import java.time.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
  private final OrderRepository repository;
  private final OrderAccess access;
  private final CatalogClient catalog;
  private final AuditRepository audit;
  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final InventoryRecovery inventory;

  public OrderService(
      OrderRepository r,
      OrderAccess a,
      CatalogClient c,
      AuditRepository h,
      OutboxRepository o,
      ObjectMapper j,
      InventoryRecovery inventory) {
    repository = r;
    access = a;
    catalog = c;
    audit = h;
    outbox = o;
    json = j;
    this.inventory = inventory;
  }

  @Transactional
  public OrderResponse create(OrderRequest request) {
    Actor.roles("Cliente", "Operador", "Admin");
    access.store(request.storeId());
    var order = new CustomerOrder();
    order.setOwnerId(Actor.id());
    order.setStatus(OrderStatus.CREADO);
    apply(order, request);
    repository.saveAndFlush(order);
    record(order, "CREATED", null);
    command(order, EMAIL_KEY);
    return OrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public OrderResponse findById(Long id) {
    var o = get(id);
    access.read(o);
    return OrderResponse.from(o);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> search(
      OrderStatus status, LocalDateTime from, LocalDateTime to, int page, int size) {
    Actor.roles("Admin", "Operador", "Cliente");
    if (from != null && to != null && from.isAfter(to))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Desde debe ser anterior a hasta");
    var spec =
        OrderRepository.filter(status, from, to)
            .and(
                (root, q, cb) -> {
                  var live = cb.isFalse(root.get("deleted"));
                  if (Actor.has("Admin")) return live;
                  if (Actor.has(
                      "Operador")) { // Assignments are database-controlled, never trusted from
                    // request headers.
                    var sub = q.subquery(Long.class);
                    var op = sub.from(OperatorAssignment.class);
                    sub.select(op.get("storeId")).where(cb.equal(op.get("actorId"), Actor.id()));
                    return cb.and(live, root.get("storeId").in(sub));
                  }
                  return cb.and(live, cb.equal(root.get("ownerId"), Actor.id()));
                });
    return repository
        .findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(OrderResponse::from)
        .getContent();
  }

  @Transactional
  public OrderResponse update(Long id, OrderRequest r) {
    var o = locked(id);
    access.read(o);
    access.store(r.storeId());
    if (o.getStatus() != OrderStatus.CREADO)
      throw new InvalidStatusTransitionException("Solo se puede editar un pedido CREADO");
    apply(o, r);
    repository.saveAndFlush(o);
    record(o, "UPDATED", o.getStatus());
    return OrderResponse.from(o);
  }

  @Transactional
  public OrderResponse changeStatus(Long id, OrderStatus next) {
    var o = locked(id);
    access.read(o);
    var old = o.getStatus();
    if (Actor.has("Cliente") && !Actor.has("Admin") && !Actor.has("Operador")) {
      if (next != OrderStatus.CANCELADO || old != OrderStatus.CREADO)
        throw new org.springframework.security.access.AccessDeniedException(
            "Cliente solo puede cancelar pedidos CREADO propios");
    } else {
      Actor.roles("Admin", "Operador");
      access.store(o.getStoreId());
    }
    if (old == next)
      return OrderResponse.from(o); // Retries don't duplicate commands or stock updates.
    if (!old.canTransitionTo(next))
      throw new InvalidStatusTransitionException("Transición inválida de " + old + " a " + next);
    if (next == OrderStatus.CANCELADO && old != OrderStatus.CREADO && old != OrderStatus.ACEPTADO)
      throw new InvalidStatusTransitionException(
          "No se cancela un pedido en preparación o despacho");
    if (next == OrderStatus.ACEPTADO) {
      String reservationId = inventory.prepare(o.getId());
      catalog.reserve(o, reservationId);
      o.setReservationId(reservationId);
    }
    // Also releases a reservation if a previous acceptance failed after a remote success.
    if (next == OrderStatus.CANCELADO) inventory.releaseAfterCommit(o);
    o.setStatus(next);
    var now = LocalDateTime.now(Clock.systemUTC());
    switch (next) {
      case ACEPTADO -> o.setAcceptedAt(now);
      case DESPACHADO -> o.setDispatchedAt(now);
      case ENTREGADO -> o.setDeliveredAt(now);
      default -> {}
    }
    repository.saveAndFlush(o);
    record(o, "STATUS_CHANGED", old);
    command(o, EMAIL_KEY);
    if (next == OrderStatus.ACEPTADO) command(o, KITCHEN_KEY);
    if (next == OrderStatus.ENTREGADO) command(o, INVOICE_KEY);
    return OrderResponse.from(o);
  }

  @Transactional
  public void delete(Long id) {
    Actor.roles("Admin");
    var o = locked(id);
    if (o.getStatus() != OrderStatus.CREADO)
      throw new InvalidStatusTransitionException("Solo se elimina un pedido CREADO");
    inventory.releaseAfterCommit(o);
    o.setDeleted(true);
    record(o, "DELETED", o.getStatus());
    repository.save(o);
  }

  private CustomerOrder get(Long id) {
    return repository
        .findById(id)
        .filter(o -> !o.getDeleted())
        .orElseThrow(() -> new OrderNotFoundException(id));
  }

  private CustomerOrder locked(Long id) {
    return repository
        .lock(id)
        .filter(o -> !o.getDeleted())
        .orElseThrow(() -> new OrderNotFoundException(id));
  }

  private void apply(CustomerOrder o, OrderRequest r) {
    o.setStoreId(r.storeId());
    o.setCustomerName(r.customerName());
    o.setCustomerEmail(r.customerEmail());
    o.setDeliveryAddress(r.deliveryAddress());
    o.setNotes(r.notes());
    o.replaceItems(
        r.items().stream()
            .map(
                i -> {
                  var product = catalog.product(i.productId());
                  if (product == null
                      || !Boolean.TRUE.equals(product.active())
                      || !r.storeId().equals(product.storeId()))
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Producto inválido para este local");
                  var item = new OrderItem();
                  item.setProductId(i.productId());
                  item.setQuantity(i.quantity());
                  item.setUnitPrice(product.price());
                  return item;
                })
            .toList());
    if (o.getTotal().compareTo(new java.math.BigDecimal("9999999999.99")) > 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total supera el límite permitido");
  }

  private void record(CustomerOrder o, String action, OrderStatus previous) {
    var h = new AuditEntry();
    h.orderId = o.getId();
    h.storeId = o.getStoreId();
    h.actorId = Actor.id();
    h.action = action;
    h.previousStatus = previous == null ? null : previous.name();
    h.status = o.getStatus().name();
    h.occurredAt = Instant.now();
    h.correlationId = MDC.get("correlationId");
    audit.save(h);
  }

  private void command(CustomerOrder o, String type) {
    var e = new OutboxEntry();
    e.id = UUID.randomUUID().toString();
    e.routingKey = type;
    e.createdAt = Instant.now();
    e.nextAttempt = e.createdAt;
    try {
      e.payload =
          json.writeValueAsString(
              Map.of(
                  "eventId",
                  e.id,
                  "type",
                  type,
                  "timestamp",
                  e.createdAt.toString(),
                  "correlationId",
                  Optional.ofNullable(MDC.get("correlationId")).orElse(e.id),
                  "ownerId",
                  o.getOwnerId(),
                  "payload",
                  OrderResponse.from(o)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    outbox.save(e);
  }
}
