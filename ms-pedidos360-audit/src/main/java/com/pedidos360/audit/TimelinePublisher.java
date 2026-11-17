package com.pedidos360.audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.stream.*;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class TimelinePublisher {
  private final AuditRepository records;
  private final EventPublisher publisher;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  public TimelinePublisher(AuditRepository records, EventPublisher publisher,
      ObjectMapper json, TransactionTemplate tx) {
    this.records = records; this.publisher = publisher; this.json = json; this.tx = tx;
  }
  @Scheduled(fixedDelayString = "${app.kafka.outbox-delay:1000}")
  public void publish() {
    for (var candidate : records.findByPublishedAtIsNullAndNextAttemptLessThanEqualOrderByOccurredAt(
        Instant.now(), PageRequest.of(0, 25))) {
      try {
        tx.executeWithoutResult(s -> {
          var row = records.lock(candidate.id).orElseThrow();
          if (row.publishedAt != null || row.nextAttempt.isAfter(Instant.now())) return;
          row.attempts = Math.min(Integer.MAX_VALUE - 1, row.attempts) + 1;
          try {
            publisher.publish(BusinessEvent.TIMELINE, json.readValue(row.payload, BusinessEvent.class));
            row.publishedAt = Instant.now(); row.lastError = null;
          } catch (Exception error) {
            row.lastError = error.getClass().getSimpleName();
            row.nextAttempt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(row.attempts, 8)));
            LoggerFactory.getLogger(getClass()).warn("Timeline pending eventId={} error={}", row.id, row.lastError);
          }
          records.save(row);
        });
      } catch (Exception error) {
        LoggerFactory.getLogger(getClass()).warn("Timeline transaction failed eventId={} error={}",
            candidate.id, error.getClass().getSimpleName());
      }
    }
  }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class AuditScheduling {}

