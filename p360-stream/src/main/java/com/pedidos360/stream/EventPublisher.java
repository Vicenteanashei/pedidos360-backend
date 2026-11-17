package com.pedidos360.stream;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class EventPublisher {
  private final KafkaTemplate<String, Object> template;
  public EventPublisher(KafkaTemplate<String, Object> template) { this.template = template; }

  public void publish(String topic, BusinessEvent event) throws Exception {
    if (!topic.equals(BusinessEvent.ORDERS) && !topic.equals(BusinessEvent.TIMELINE))
      throw new IllegalArgumentException("Tópico de negocio no permitido");
    // Business events retain order affinity. Timeline uses eventId so compaction keeps every event.
    String key = topic.equals(BusinessEvent.ORDERS) ? event.payload().id().toString() : event.eventId();
    template.send(topic, key, event).get(35, TimeUnit.SECONDS);
  }
}

