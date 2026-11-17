package com.pedidos360.kafkaadmin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.Test;
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
}

