package com.pedidos360.audit;
import com.pedidos360.stream.BusinessEvent;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class AuditConsumers {
  private final AuditProcessor processor;
  public AuditConsumers(AuditProcessor processor) { this.processor = processor; }
  @KafkaListener(topics = BusinessEvent.ORDERS, groupId = "p360-audit-ingest", containerFactory = "streamListenerFactory")
  public void ingest(BusinessEvent event) { handle(event, false); }
  @KafkaListener(topics = BusinessEvent.TIMELINE, groupId = "p360-audit-timeline", containerFactory = "streamListenerFactory")
  public void project(BusinessEvent event) { handle(event, true); }
  private void handle(BusinessEvent event, boolean project) {
    try {
      MDC.put("correlationId", event.correlationId());
      if (project) processor.project(event); else processor.ingest(event);
      LoggerFactory.getLogger(getClass()).info("Kafka audit processed eventId={} stage={}",
          event.eventId(), project ? "timeline" : "ingress");
    } finally { MDC.remove("correlationId"); }
  }
}

