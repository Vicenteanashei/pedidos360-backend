package com.pedidos360.report;
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
@EmbeddedKafka(partitions = 3, topics = {"orders.events", "orders.events.report.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaFlowTest {
  @Autowired KafkaTemplate<String, Object> template;
  @Autowired ReportRepository repository;
  @Autowired EmbeddedKafkaBroker broker;

  @Test void brokerDeliveryIsIdempotentAndPoisonMessagesReachDltWithoutBlockingValidEvents() throws Exception {
    long id = 123456;
    var event = ReportProjectionTest.event(id, 5, "ENTREGADO", "OrderDelivered", "STATUS_CHANGED");
    var props = KafkaTestUtils.consumerProps("dlt-check-" + UUID.randomUUID(), "false", broker);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    try (var dlt = new KafkaConsumer<String, byte[]>(props, new StringDeserializer(), new ByteArrayDeserializer())) {
      dlt.subscribe(List.of("orders.events.report.DLT"));
      template.send(BusinessEvent.ORDERS, "invalid", "{bad-json".getBytes(java.nio.charset.StandardCharsets.UTF_8)).get();
      template.send(BusinessEvent.ORDERS, Long.toString(id), event).get();
      template.send(BusinessEvent.ORDERS, Long.toString(id), event).get();
      await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
        var projected = repository.findById(id);
        assertTrue(projected.isPresent());
        assertEquals(5, projected.orElseThrow().eventVersion);
      });
      var invalid = new ArrayList<ConsumerRecord<String, byte[]>>();
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (invalid.isEmpty() && System.nanoTime() < deadline)
        dlt.poll(Duration.ofMillis(500)).forEach(invalid::add);
      assertFalse(invalid.isEmpty(), "Poison message must reach the report-specific DLT");
      assertEquals("invalid", invalid.getFirst().key());
      assertArrayEquals("{bad-json".getBytes(java.nio.charset.StandardCharsets.UTF_8), invalid.getFirst().value());
      assertTrue(invalid.getFirst().headers().toArray().length > 0);
    }
  }
}

