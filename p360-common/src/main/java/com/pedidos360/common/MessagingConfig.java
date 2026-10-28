package com.pedidos360.common;

import java.util.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.*;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.messaging.enabled",
    havingValue = "true")
public class MessagingConfig {
  public static final String DIRECT_EXCHANGE = "cmd.direct";
  public static final String TOPIC_EXCHANGE = "cmd.topic";
  public static final String DEAD_EXCHANGE = "cmd.dead.dlx";
  public static final String EMAIL_QUEUE = "q.cmd.email";
  public static final String KITCHEN_QUEUE = "q.cmd.kitchen";
  public static final String INVOICE_QUEUE = "q.cmd.invoice";
  public static final String EMAIL_KEY = "email.send";
  public static final String KITCHEN_KEY = "kitchen.ticket";
  public static final String INVOICE_KEY = "invoice.gen";

  // One shared definition for topology, producers, consumers and DLQ inspection.
  public enum Task {
    EMAIL(EMAIL_QUEUE, EMAIL_KEY, "email.#"),
    KITCHEN(KITCHEN_QUEUE, KITCHEN_KEY, "kitchen.#"),
    INVOICE(INVOICE_QUEUE, INVOICE_KEY, "invoice.*");

    private final String queue;
    private final String routingKey;
    private final String topicPattern;

    Task(String queue, String routingKey, String topicPattern) {
      this.queue = queue;
      this.routingKey = routingKey;
      this.topicPattern = topicPattern;
    }

    public String queue() { return queue; }
    public String routingKey() { return routingKey; }
    public String topicPattern() { return topicPattern; }
    public String dlq() { return queue + ".dlq"; }
  }

  public static final List<Task> TASKS = List.of(Task.values());
  public static final Set<String> DLQ_NAMES =
      Set.of(Task.EMAIL.dlq(), Task.KITCHEN.dlq(), Task.INVOICE.dlq());
  public static final Set<String> TASK_TYPES =
      Set.of(EMAIL_KEY, KITCHEN_KEY, INVOICE_KEY);

  @Bean
  Jackson2JsonMessageConverter jsonMessages() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  Declarables topology() {
    var list = new ArrayList<Declarable>();
    list.add(new DirectExchange(DIRECT_EXCHANGE, true, false));
    list.add(new TopicExchange(TOPIC_EXCHANGE, true, false));
    list.add(new DirectExchange(DEAD_EXCHANGE, true, false));
    for (Task task : TASKS) {
      String q = task.queue();
      // DLX settings are supplied by infra/mq/policies.json before producers start.
      // Existing queues with hardcoded arguments require the guarded empty-queue migration.
      list.add(QueueBuilder.durable(q).build());
      list.add(QueueBuilder.durable(task.dlq()).build());
      list.add(new Binding(q, Binding.DestinationType.QUEUE, DIRECT_EXCHANGE, task.routingKey(), Map.of()));
      list.add(new Binding(q, Binding.DestinationType.QUEUE, TOPIC_EXCHANGE, task.topicPattern(), Map.of()));
      list.add(
          new Binding(
              task.dlq(), Binding.DestinationType.QUEUE, DEAD_EXCHANGE, task.routingKey(), Map.of()));
    }
    return new Declarables(list);
  }
}
