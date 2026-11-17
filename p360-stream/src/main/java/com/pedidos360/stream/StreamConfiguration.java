package com.pedidos360.stream;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.kafka.support.serializer.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class StreamConfiguration {
  @Bean
  ProducerFactory<String, Object> streamProducerFactory(KafkaProperties properties) {
    var config = new HashMap<String, Object>(properties.buildProducerProperties());
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
    config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
    var json = new JsonSerializer<BusinessEvent>();
    json.setAddTypeInfo(false);
    Map<Class<?>, Serializer<?>> serializers = new HashMap<>();
    serializers.put(BusinessEvent.class, json);
    serializers.put(byte[].class, new ByteArraySerializer());
    return new DefaultKafkaProducerFactory<>(config, new StringSerializer(),
        new DelegatingByTypeSerializer(serializers));
  }

  @Bean
  KafkaTemplate<String, Object> streamTemplate(ProducerFactory<String, Object> factory) {
    return new KafkaTemplate<>(factory);
  }

  @Bean
  ConsumerFactory<String, BusinessEvent> streamConsumerFactory(KafkaProperties properties) {
    var config = new HashMap<String, Object>(properties.buildConsumerProperties());
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
    var json = new JsonDeserializer<>(BusinessEvent.class, false);
    json.addTrustedPackages("com.pedidos360.stream");
    return new DefaultKafkaConsumerFactory<>(config,
        new ErrorHandlingDeserializer<>(new StringDeserializer()),
        new ErrorHandlingDeserializer<>(json));
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, BusinessEvent> streamListenerFactory(
      ConsumerFactory<String, BusinessEvent> factory, KafkaTemplate<String, Object> template,
      @org.springframework.beans.factory.annotation.Value("${app.kafka.consumer-concurrency:3}") int concurrency) {
    var recoverer = new DeadLetterPublishingRecoverer(template, (record, error) -> {
      String group = KafkaUtilsGroup.current();
      String suffix = group.equals("p360-report") ? "report" :
          group.equals("p360-audit-ingest") ? "audit" : "timeline";
      return new org.apache.kafka.common.TopicPartition(record.topic() + "." + suffix + ".DLT", record.partition());
    });
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(35));
    var errors = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    errors.addNotRetryableExceptions(IllegalArgumentException.class);
    errors.setRetryListeners((record, error, attempt) ->
        LoggerFactory.getLogger(StreamConfiguration.class).warn(
            "Kafka processing failed topic={} partition={} offset={} attempt={} error={}",
            record.topic(), record.partition(), record.offset(), attempt, error.getClass().getSimpleName()));
    var listener = new ConcurrentKafkaListenerContainerFactory<String, BusinessEvent>();
    listener.setConsumerFactory(factory);
    listener.setConcurrency(Math.max(1, Math.min(concurrency, 3)));
    listener.setCommonErrorHandler(errors);
    listener.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    listener.getContainerProperties().setDeliveryAttemptHeader(true);
    return listener;
  }

  private static class KafkaUtilsGroup {
    static String current() {
      String group = org.springframework.kafka.support.KafkaUtils.getConsumerGroupId();
      return group == null ? "" : group;
    }
  }
}

