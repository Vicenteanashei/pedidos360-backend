package com.pedidos360.report;
import com.pedidos360.stream.BusinessEvent;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class ReportConsumer {
  private final ReportProcessor processor;
  public ReportConsumer(ReportProcessor processor) { this.processor = processor; }
  @KafkaListener(topics = BusinessEvent.ORDERS, groupId = "p360-report", containerFactory = "streamListenerFactory")
  public void receive(BusinessEvent event) {
    try {
      MDC.put("correlationId", event.correlationId());
      processor.process(event);
      LoggerFactory.getLogger(getClass()).info("Kafka report processed eventId={}", event.eventId());
    } finally { MDC.remove("correlationId"); }
  }
}

