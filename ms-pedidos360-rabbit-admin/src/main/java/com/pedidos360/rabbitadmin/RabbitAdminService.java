package com.pedidos360.rabbitadmin;

import static com.pedidos360.rabbitadmin.dto.RabbitInputs.*;

import com.pedidos360.rabbitadmin.dto.RabbitInputs;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RabbitAdminService {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RabbitAdminService.class);
  private final RestClient api;
  private final String vhost;
  private final ObjectMapper json;

  public RabbitAdminService(@Qualifier("rabbitManagementClient") RestClient api,
      @Value("${spring.rabbitmq.virtual-host}") String vhost, ObjectMapper json) {
    this.api = api;
    this.vhost = vhost;
    this.json = json;
  }

  public Map<?, ?> overview() {
    return api.get().uri("/overview").retrieve().body(Map.class);
  }

  public List<?> nodes() {
    return api.get().uri("/nodes").retrieve().body(List.class);
  }

  public List<?> queues() {
    return api.get().uri("/queues/{v}", vhost).retrieve().body(List.class);
  }

  public List<?> exchanges() {
    return api.get().uri("/exchanges/{v}", vhost).retrieve().body(List.class);
  }

  public List<?> bindings() {
    return api.get().uri("/bindings/{v}", vhost).retrieve().body(List.class);
  }

  private void testOnly(String name) {
    if (!name.matches(DEMO_PATTERN))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Las operaciones de administración usan recursos demo.* para proteger las colas"
              + " operativas");
  }

  public void createQueue(QueueInput r) {
    testOnly(r.name());
    api.put()
        .uri("/queues/{v}/{q}", vhost, r.name())
        .body(Map.of("durable", true, "auto_delete", false, "arguments", Map.of()))
        .retrieve()
        .toBodilessEntity();
  }

  public void deleteQueue(String name) {
    testOnly(name);
    api.delete()
        .uri("/queues/{v}/{q}?if-empty=true&if-unused=true", vhost, name)
        .retrieve()
        .toBodilessEntity();
  }

  public void createExchange(ExchangeInput r) {
    testOnly(r.name());
    api.put()
        .uri("/exchanges/{v}/{e}", vhost, r.name())
        .body(
            Map.of(
                "type",
                r.type(),
                "durable",
                true,
                "auto_delete",
                false,
                "internal",
                false,
                "arguments",
                Map.of()))
        .retrieve()
        .toBodilessEntity();
  }

  public void deleteExchange(String name) {
    testOnly(name);
    api.delete()
        .uri("/exchanges/{v}/{e}?if-unused=true", vhost, name)
        .retrieve()
        .toBodilessEntity();
  }

  public void bind(BindingInput r) {
    testOnly(r.exchange());
    testOnly(r.queue());
    api.post()
        .uri("/bindings/{v}/e/{e}/q/{q}", vhost, r.exchange(), r.queue())
        .body(Map.of("routing_key", r.routingKey(), "arguments", Map.of()))
        .retrieve()
        .toBodilessEntity();
  }

  public void unbind(
      String exchange,
      String queue,
      String propertiesKey) {
    if (propertiesKey == null || !propertiesKey.matches(RabbitInputs.PROPERTIES_KEY_PATTERN))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "propertiesKey inválido");
    testOnly(exchange);
    testOnly(queue);
    api.delete()
        .uri("/bindings/{v}/e/{e}/q/{q}/{k}", vhost, exchange, queue, propertiesKey)
        .retrieve()
        .toBodilessEntity();
  }

  // Management's requeue mode leaves DLQ evidence in place after inspection.
  public List<?> inspect(String name) {
    if (!com.pedidos360.common.MessagingConfig.DLQ_NAMES.contains(name)
        && !name.matches(DEMO_PATTERN))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var messages =
        api.post()
            .uri("/queues/{v}/{q}/get", vhost, name)
            .body(
                Map.of(
                    "count",
                    10,
                    "ackmode",
                    "ack_requeue_true",
                    "encoding",
                    "auto",
                    "truncate",
                    10000))
            .retrieve()
            .body(List.class);
    if (name.endsWith(".dlq") && messages != null) {
      for (var message : messages) {
        String id = "no-disponible", type = "no-disponible";
        try {
          if (message instanceof Map<?, ?> metadata
              && metadata.get("payload") instanceof String payload) {
            var event = json.readTree(payload);
            id = UUID.fromString(event.path("eventId").asText()).toString();
            String candidate = event.path("type").asText();
            if (com.pedidos360.common.MessagingConfig.TASK_TYPES.contains(candidate))
              type = candidate;
          }
        } catch (Exception invalidEnvelope) {
          // A malformed/truncated payload remains inspectable; never log its body or exception.
        }
        log.info("DLQ inspeccion queue={} eventId={} type={}", name, id, type);
      }
    }
    return messages;
  }
}
