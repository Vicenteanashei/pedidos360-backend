package com.pedidos360.kafkaadmin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Collection;
import java.util.Map;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class KafkaPolicyTest {
  @Test void businessAndInternalTopicsCannotBeDeletedEvenByAdmin() {
    var broker = mock(Admin.class);
    var service = new KafkaPlatformService(broker);
    for (String topic : java.util.List.of("orders.events", "audit.timeline", "__consumer_offsets", "../x", "demo..x")) {
      assertThrows(ResponseStatusException.class, () -> service.delete(topic));
    }
    verifyNoInteractions(broker);
  }
  @Test void topologyPoliciesCannotBeWeakened() {
    var broker = mock(Admin.class);
    var service = new KafkaPlatformService(broker);
    assertThrows(ResponseStatusException.class, () ->
        service.update("orders.events", new KafkaController.TopicChange(4, null, null)));
    assertThrows(ResponseStatusException.class, () ->
        service.update("audit.timeline", new KafkaController.TopicChange(null, 3600000L, "delete")));
    verifyNoInteractions(broker);
  }

  @Test void removingOverridesIsLimitedToEditablePropertiesOnDemoTopics() {
    var broker = mock(Admin.class);
    var service = new KafkaPlatformService(broker);
    var protectedTopic = assertThrows(ResponseStatusException.class,
        () -> service.resetConfiguration("audit.timeline", "retention.ms"));
    var protectedProperty = assertThrows(ResponseStatusException.class,
        () -> service.resetConfiguration("demo.test", "min.insync.replicas"));
    assertEquals(HttpStatus.FORBIDDEN, protectedTopic.getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, protectedProperty.getStatusCode());
    verifyNoInteractions(broker);
  }

  @Test void removingOneOverrideUsesKafkaDeleteForThatPropertyOnly() {
    var broker = mock(Admin.class);
    var result = mock(AlterConfigsResult.class);
    when(broker.incrementalAlterConfigs(anyMap())).thenReturn(result);
    when(result.all()).thenReturn(KafkaFuture.completedFuture(null));

    new KafkaPlatformService(broker).resetConfiguration("demo.test", "cleanup.policy");

    var resource = new ConfigResource(ConfigResource.Type.TOPIC, "demo.test");
    verify(broker).incrementalAlterConfigs(argThat((Map<ConfigResource, Collection<AlterConfigOp>> changes) -> {
      if (changes.size() != 1 || !changes.containsKey(resource) || changes.get(resource).size() != 1)
        return false;
      var operation = changes.get(resource).iterator().next();
      return operation.opType() == AlterConfigOp.OpType.DELETE
          && operation.configEntry().name().equals("cleanup.policy")
          && operation.configEntry().value() == null;
    }));
  }
}

