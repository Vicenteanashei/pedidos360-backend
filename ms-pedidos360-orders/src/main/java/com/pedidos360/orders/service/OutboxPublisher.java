package com.pedidos360.orders.service;

import com.pedidos360.orders.repository.OutboxRepository;
import java.time.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.outbox.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisher {
  private final OutboxRepository outbox;
  private final RabbitTemplate rabbit;
  private final TransactionTemplate tx;

  public OutboxPublisher(OutboxRepository o, RabbitTemplate r, TransactionTemplate t) {
    outbox = o;
    rabbit = r;
    tx = t;
  }

  @Scheduled(fixedDelayString = "${app.outbox-delay:2000}")
  public void publish() {
    for (var candidate :
        outbox
            .findByPublishedAtIsNullAndAttemptsLessThanAndNextAttemptLessThanEqualOrderByCreatedAt(
                10, Instant.now(), PageRequest.of(0, 25)))
      tx.executeWithoutResult(
          s -> {
            var e = outbox.lock(candidate.id).orElseThrow();
            if (e.publishedAt != null) return;
            e.attempts++;
            try {
              var p = new MessageProperties();
              p.setContentType("application/json");
              p.setMessageId(e.id);
              p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
              var correlation = new CorrelationData(e.id);
              rabbit.send(
                  com.pedidos360.common.MessagingConfig.DIRECT_EXCHANGE,
                  e.routingKey,
                  new Message(e.payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), p),
                  correlation);
              var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
              if (!confirm.isAck() || correlation.getReturned() != null)
                throw new IllegalStateException("Publicación no confirmada o sin ruta");
              e.publishedAt = Instant.now();
              e.lastError = null;
            } catch (Exception ex) {
              e.lastError = "Publicación pendiente: " + ex.getClass().getSimpleName();
              e.nextAttempt =
                  Instant.now().plusSeconds(Math.min(300, 1L << Math.min(e.attempts, 8)));
            }
            outbox.save(e);
          });
  }
}
