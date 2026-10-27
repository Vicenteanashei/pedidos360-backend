package com.pedidos360.orders.service;

import com.pedidos360.orders.model.*;
import com.pedidos360.orders.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryRecovery {
  private final InventoryRepository operations;
  private final OrderRepository orders;
  private final CatalogClient catalog;
  private final TransactionTemplate independent;

  public InventoryRecovery(
      InventoryRepository r,
      OrderRepository o,
      CatalogClient c,
      PlatformTransactionManager manager) {
    operations = r;
    orders = o;
    catalog = c;
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  // Each acceptance attempt has an independent durable ID. Retrying cannot overwrite an older
  // recovery intent.
  public String prepare(Long orderId) {
    String id = "order-" + orderId + "-" + UUID.randomUUID();
    independent.executeWithoutResult(
        tx -> {
          var op = new InventoryOperation();
          op.id = id;
          op.orderId = orderId;
          op.action = "RESERVE";
          op.completed = false;
          op.updatedAt = Instant.now();
          operations.saveAndFlush(op);
        });
    return id;
  }

  public void releaseAfterCommit(CustomerOrder order) {
    if (order.getReservationId() == null) return;
    var op = operations.findById(order.getReservationId()).orElseThrow();
    op.action = "RELEASE";
    op.completed = false;
    op.updatedAt = Instant.now();
    operations.save(op);
  }

  @Scheduled(fixedDelay = 5000)
  public void reconcile() {
    for (var candidate : operations.findByCompletedFalse(PageRequest.of(0, 25)))
      try {
        independent.executeWithoutResult(
            tx -> {
              // Lock order first, like foreground requests. Only the committed reservation ID is
              // retained.
              var order = orders.lock(candidate.orderId).orElse(null);
              if (order == null) return;
              var op = operations.findById(candidate.id).orElseThrow();
              if (op.completed) return;
              boolean retain =
                  op.id.equals(order.getReservationId())
                      && order.getStatus() != OrderStatus.CREADO
                      && order.getStatus() != OrderStatus.CANCELADO
                      && !order.getDeleted();
              try {
                if (op.action.equals("RELEASE") || !retain) catalog.release(op.id);
                op.completed = true;
                op.lastError = null;
              } catch (Exception e) {
                op.lastError = "Recuperación pendiente: " + e.getClass().getSimpleName();
              }
              op.updatedAt = Instant.now();
              operations.save(op);
            });
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(InventoryRecovery.class)
            .warn(
                "Recuperación pendiente orderId={} error={}",
                candidate.orderId,
                e.getClass().getSimpleName());
      }
  }
}
