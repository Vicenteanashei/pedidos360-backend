package com.pedidos360.kafkaadmin;
import java.util.HashMap;
import org.apache.kafka.clients.admin.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaAdminConfiguration {
  @Bean(destroyMethod = "close")
  Admin kafkaPlatformAdmin(KafkaProperties properties, org.springframework.boot.ssl.SslBundles bundles) {
    var config = new HashMap<String, Object>(properties.buildAdminProperties(bundles));
    config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 8000);
    return Admin.create(config);
  }
}

