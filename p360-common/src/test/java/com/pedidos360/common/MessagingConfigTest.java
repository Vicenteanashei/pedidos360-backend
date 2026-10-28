package com.pedidos360.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

class MessagingConfigTest {
  @Test
  void policiesOwnDeadLetterSettingsAndEachTaskHasAnExplicitRoute() {
    var topology = new MessagingConfig().topology();
    var queues = topology.getDeclarablesByType(Queue.class);
    var bindings = topology.getDeclarablesByType(Binding.class);
    assertEquals(6, queues.size());
    assertEquals(9, bindings.size());
    for (var queue : queues) {
      assertTrue(queue.isDurable());
      assertFalse(queue.isAutoDelete());
      assertFalse(queue.getArguments().containsKey("x-dead-letter-exchange"));
      assertFalse(queue.getArguments().containsKey("x-dead-letter-routing-key"));
    }
    for (var task : MessagingConfig.TASKS) {
      assertTrue(bindings.stream().anyMatch(b -> b.getExchange().equals(MessagingConfig.DEAD_EXCHANGE)
          && b.getDestination().equals(task.dlq()) && b.getRoutingKey().equals(task.routingKey())));
    }
    assertEquals(List.of("email.#", "kitchen.#", "invoice.*"),
        MessagingConfig.TASKS.stream().map(MessagingConfig.Task::topicPattern).toList());
  }

  @Test
  void exactTaskKeysUseDirectAndVariantsUseTopic() {
    assertEquals(MessagingConfig.DIRECT_EXCHANGE, MessagingConfig.exchangeFor("email.send"));
    assertEquals(MessagingConfig.DIRECT_EXCHANGE, MessagingConfig.exchangeFor("invoice.gen"));
    assertEquals(MessagingConfig.TOPIC_EXCHANGE, MessagingConfig.exchangeFor(MessagingConfig.EMAIL_HIGH_KEY));
    assertEquals(
        java.util.Set.of("q.cmd.email.dlq", "q.cmd.kitchen.dlq", "q.cmd.invoice.dlq"),
        MessagingConfig.DLQ_NAMES);
  }
}
