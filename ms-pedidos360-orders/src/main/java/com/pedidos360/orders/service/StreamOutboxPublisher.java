package com.pedidos360.orders.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.orders.repository.StreamOutboxRepository;
import com.pedidos360.stream.*;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class StreamOutboxPublisher {
  private final StreamOutboxRepository outbox;
  private final EventPublisher publisher;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  public StreamOutboxPublisher(StreamOutboxRepository outbox, EventPublisher publisher,
      ObjectMapper json, TransactionTemplate tx) {
    this.outbox = outbox; this.publisher = publisher; this.json = json; this.tx = tx;
  }
  @Scheduled(fixedDelayString = "${app.kafka.outbox-delay:1000}")
  public void publish() {
    for (var candidate : outbox.ready(Instant.now(), PageRequest.of(0, 25))) {
      try {
        tx.executeWithoutResult(s -> {
          var e = outbox.lock(candidate.id).orElseThrow();
          if (e.publishedAt != null || e.nextAttempt.isAfter(Instant.now())) return;
          e.attempts = Math.min(Integer.MAX_VALUE - 1, e.attempts) + 1;
          try {
            publisher.publish(BusinessEvent.ORDERS, json.readValue(e.payload, BusinessEvent.class));
            e.publishedAt = Instant.now(); e.lastError = null;
          } catch (Exception ex) {
            e.lastError = ex.getClass().getSimpleName();
            e.nextAttempt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(e.attempts, 8)));
            LoggerFactory.getLogger(getClass()).warn("Kafka outbox pending eventId={} error={}", e.id, e.lastError);
          }
          outbox.save(e);
        });
      } catch (Exception ex) {
        LoggerFactory.getLogger(getClass()).warn("Kafka outbox transaction failed eventId={} error={}",
            candidate.id, ex.getClass().getSimpleName());
      }
    }
  }
}

