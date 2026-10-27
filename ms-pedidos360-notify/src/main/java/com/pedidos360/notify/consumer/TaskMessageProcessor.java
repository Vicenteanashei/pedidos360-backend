package com.pedidos360.notify.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.common.MessagingConfig.Task;
import com.pedidos360.notify.DeliveryService;
import com.pedidos360.notify.DemoDeliveryFailureException;
import com.pedidos360.notify.config.ConsumerPolicy;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

@Component
public class TaskMessageProcessor {
  private static final Logger log = LoggerFactory.getLogger(TaskMessageProcessor.class);
  private final DeliveryService delivery;
  private final ObjectMapper json;
  private final ConsumerPolicy policy;

  public TaskMessageProcessor(DeliveryService delivery, ObjectMapper json, ConsumerPolicy policy) {
    this.delivery = delivery;
    this.json = json;
    this.policy = policy;
  }

  public void consume(Message message, Channel channel, Task task) throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    JsonNode event;
    try {
      event = json.readTree(message.getBody());
      if (event == null || !event.isObject()) throw new IllegalArgumentException("Invalid envelope");
    } catch (Exception invalidEnvelope) {
      deadLetter(channel, tag, task, "no-disponible", 1, invalidEnvelope);
      return;
    }
    String eventId = safeId(event);
    int attempt = 1;
    while (true) {
      // DeliveryService is transactional: returning also confirms its DB transaction committed.
      // A failure while sending basicAck is deliberately OUTSIDE this catch. The broker can
      // redeliver after channel recovery and the persisted receipt makes that retry idempotent.
      try {
        delivery.process(event, task.routingKey());
      } catch (Exception error) {
        if (!retryable(error) || attempt >= policy.maxAttempts()) {
          deadLetter(channel, tag, task, eventId, attempt, error);
          return;
        }
        log.warn("Rabbit RETRY queue={} eventId={} type={} attempt={} error={}",
            task.queue(), eventId, task.routingKey(), attempt, error.getClass().getSimpleName());
        long delay = Math.min(policy.maxDelay(), policy.initialDelay() * (1L << (attempt - 1)));
        try {
          Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          // Shutdown interruption is not a poison message. Preserve it for the next consumer.
          channel.basicNack(tag, false, true);
          log.info("Rabbit REQUEUE_INTERRUPTED queue={} eventId={} type={}",
              task.queue(), eventId, task.routingKey());
          return;
        }
        attempt++;
        continue;
      }
      channel.basicAck(tag, false);
      log.info("Rabbit ACK queue={} eventId={} type={} attempts={}",
          task.queue(), eventId, task.routingKey(), attempt);
      return;
    }
  }

  private boolean retryable(Exception error) {
    if (error instanceof DataIntegrityViolationException) return false;
    return error instanceof DemoDeliveryFailureException
        || error instanceof DataAccessException
        || error instanceof IOException
        || error instanceof MailException;
  }

  private void deadLetter(Channel channel, long tag, Task task, String eventId,
      int attempts, Exception error) throws IOException {
    // Reject without requeue routes through the already declared DLX to this task's DLQ.
    // Never log bodies, customer details, exception messages, credentials or JWTs.
    channel.basicNack(tag, false, false);
    log.error("Rabbit NACK_DLQ queue={} dlq={} eventId={} type={} attempts={} error={}",
        task.queue(), task.dlq(), eventId, task.routingKey(), attempts, error.getClass().getSimpleName());
  }

  private String safeId(JsonNode event) {
    try { return UUID.fromString(event.path("eventId").asText()).toString(); }
    catch (IllegalArgumentException invalid) { return "no-disponible"; }
  }
}
