package com.pedidos360.audit;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import com.pedidos360.stream.BusinessEvent;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {"app.kafka.enabled=true", "app.kafka.consumer-concurrency=1"})
@EmbeddedKafka(partitions = 3, topics = {"orders.events", "audit.timeline",
    "orders.events.audit.DLT", "audit.timeline.timeline.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class AuditKafkaFlowTest {
  @Autowired KafkaTemplate<String, Object> template;
  @Autowired AuditRepository repository;
  @Autowired TimelinePublisher publisher;
  @Autowired EmbeddedKafkaBroker broker;
  @Test void bothTopicsAreProducedAndConsumedWithUniqueTimelineKeysAndNoDuplicateHistory() throws Exception {
    var first = AuditProjectionTest.event(1);
    var second = AuditProjectionTest.event(2);
    template.send(BusinessEvent.ORDERS, "1", first).get();
    template.send(BusinessEvent.ORDERS, "1", second).get();
    template.send(BusinessEvent.ORDERS, "1", first).get();
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      assertTrue(repository.findById(first.eventId()).isPresent());
      assertTrue(repository.findById(second.eventId()).isPresent());
    });
    publisher.publish();
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      assertNotNull(repository.findById(first.eventId()).orElseThrow().projectedAt);
      assertNotNull(repository.findById(second.eventId()).orElseThrow().projectedAt);
    });
    var props = KafkaTestUtils.consumerProps("timeline-check-" + UUID.randomUUID(), "false", broker);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    var keys = new HashSet<String>();
    try (var reader = new KafkaConsumer<String, String>(props, new StringDeserializer(), new StringDeserializer())) {
      reader.subscribe(List.of(BusinessEvent.TIMELINE));
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (!keys.containsAll(Set.of(first.eventId(), second.eventId())) && System.nanoTime() < deadline)
        reader.poll(Duration.ofMillis(500)).forEach(r -> keys.add(r.key()));
    }
    assertTrue(keys.containsAll(Set.of(first.eventId(), second.eventId())));
    assertEquals(2, repository.count());
  }
}

