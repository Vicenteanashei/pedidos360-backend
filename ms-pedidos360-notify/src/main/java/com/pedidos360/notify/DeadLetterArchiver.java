package com.pedidos360.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.common.MessagingConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Archiva en la base de notify los mensajes muertos que llegan a las DLQ. */
@Service
public class DeadLetterArchiver {
  static final int MAX_PAYLOAD = 4000;
  private final DeadLetterRepository repository;
  private final ObjectMapper json;

  public DeadLetterArchiver(DeadLetterRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  /** Devuelve lo archivado; un mensaje ya archivado (redelivery) no se duplica. */
  @Transactional
  public DeadLetter archive(Message message) {
    var properties = message.getMessageProperties();
    String id = properties.getMessageId();
    if (id == null || id.isBlank() || id.length() > 64) id = UUID.randomUUID().toString();
    var existing = repository.findById(id);
    if (existing.isPresent()) return existing.get();

    var entry = new DeadLetter();
    entry.id = id;
    entry.dlq = cut(properties.getConsumerQueue(), 60, "desconocida");
    entry.deathCount = 1L;
    var deaths = properties.getXDeathHeader();
    if (deaths != null && !deaths.isEmpty()) {
      // La primera entrada de x-death es la muerte más reciente.
      var death = deaths.getFirst();
      entry.originalQueue = cut(text(death.get("queue")), 60, null);
      entry.originalExchange = cut(text(death.get("exchange")), 60, null);
      entry.reason = cut(text(death.get("reason")), 40, null);
      if (death.get("routing-keys") instanceof List<?> keys && !keys.isEmpty())
        entry.originalRoutingKey = cut(text(keys.getFirst()), 150, null);
      if (death.get("count") instanceof Number count) entry.deathCount = count.longValue();
    }
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
      var event = json.readTree(body);
      entry.eventId = UUID.fromString(event.path("eventId").asText()).toString();
      String type = event.path("type").asText();
      if (MessagingConfig.TASK_TYPES.contains(type)) entry.type = type;
    } catch (Exception notAnEnvelope) {
      // Un payload inválido también se archiva: justamente por eso llegó a la DLQ.
    }
    entry.payload = body.isEmpty() ? "(vacío)" : cut(body, MAX_PAYLOAD, "");
    entry.archivedAt = Instant.now();
    return repository.saveAndFlush(entry);
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private static String cut(String value, int max, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value.length() <= max ? value : value.substring(0, max);
  }
}
