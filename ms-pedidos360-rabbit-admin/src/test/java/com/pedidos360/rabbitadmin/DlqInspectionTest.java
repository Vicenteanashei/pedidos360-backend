package com.pedidos360.rabbitadmin;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

class DlqInspectionTest {
  @Test
  void rejectsNamesThatCouldInjectLogLinesBeforeNetwork() {
    var controller =
        new RabbitAdminService(RestClient.builder().baseUrl("http://127.0.0.1:1/api").build(), "/pedidos360", new ObjectMapper());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> controller.inspect("demo.bad\nforged.dlq"));
  }

  @Test
  void logsOnlyIdentifiersAndPreservesMessagesWithRequeue() throws Exception {
    var json = new ObjectMapper();
    String id = "d185cebc-1345-4e08-b921-8eb9241d578a";
    String privateData = "fixture-confidential-customer";
    var received = new AtomicReference<String>();
    var events = new ListAppender<ILoggingEvent>();
    events.start();
    var logger = (Logger) LoggerFactory.getLogger(RabbitAdminService.class);
    logger.addAppender(events);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var messages =
        List.of(
            Map.of(
                "payload",
                json.writeValueAsString(
                    Map.of("eventId", id, "type", "email.send", "customer", privateData))),
            Map.of("payload", "malformed " + privateData));
    byte[] response = json.writeValueAsBytes(messages);
    server.createContext(
        "/api/",
        exchange -> {
          received.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      var controller =
          new RabbitAdminService(RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api").build(), "/pedidos360", json);
      assertEquals(messages, controller.inspect("q.cmd.email.dlq"));
      assertEquals("ack_requeue_true", json.readTree(received.get()).path("ackmode").asText());
      assertEquals(2, events.list.size());
      String log =
          events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertTrue(log.contains(id));
      assertTrue(log.contains("email.send"));
      assertTrue(log.contains("no-disponible"));
      assertFalse(log.contains(privateData));
      assertFalse(log.contains("fixture-password"));
    } finally {
      server.stop(0);
      logger.detachAppender(events);
      events.stop();
    }
  }
}
