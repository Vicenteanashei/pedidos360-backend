package com.pedidos360.notify.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.notify.DeadLetter;
import com.pedidos360.notify.DeadLetterArchiver;
import com.pedidos360.notify.DeadLetterRepository;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

class DeadLetterConsumerTest {
  final String id = "d185cebc-1345-4e08-b921-8eb9241d578a";
  DeadLetterRepository repository;
  Channel channel;

  @BeforeEach
  void setup() {
    repository = mock(DeadLetterRepository.class);
    channel = mock(Channel.class);
    when(repository.findById(anyString())).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
  }

  Message dead(String body) {
    var properties = new MessageProperties();
    properties.setDeliveryTag(7);
    properties.setConsumerQueue("q.cmd.email.dlq");
    properties.setHeader("x-death", List.of(Map.of(
        "queue", "q.cmd.email", "reason", "rejected", "exchange", "cmd.topic",
        "routing-keys", List.of("email.send.high"), "count", 1L)));
    return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
  }

  @Test
  void archivesWithDeathDetailsThenAcknowledges() throws Exception {
    var archiver = spy(new DeadLetterArchiver(repository, new ObjectMapper()));
    var consumer = new DeadLetterConsumer(archiver);
    consumer.receive(dead("{\"eventId\":\"" + id + "\",\"type\":\"email.send\"}"), channel);

    var order = inOrder(repository, channel);
    order.verify(repository).saveAndFlush(argThat((DeadLetter d) ->
        d.dlq.equals("q.cmd.email.dlq") && d.originalQueue.equals("q.cmd.email")
            && d.reason.equals("rejected") && d.originalRoutingKey.equals("email.send.high")
            && d.eventId.equals(id) && "email.send".equals(d.type) && d.deathCount == 1L));
    order.verify(channel).basicAck(7, false);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  @Test
  void invalidPayloadIsArchivedTooWithoutEventId() throws Exception {
    var consumer = new DeadLetterConsumer(new DeadLetterArchiver(repository, new ObjectMapper()));
    consumer.receive(dead("{\"foo\":\"fallo-demo\"}"), channel);
    verify(repository).saveAndFlush(argThat((DeadLetter d) -> d.eventId == null && d.type == null));
    verify(channel).basicAck(7, false);
  }

  @Test
  void archiveFailureRequeuesAndNeverLogsTheBody() throws Exception {
    when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("db"));
    var events = new ListAppender<ILoggingEvent>();
    events.start();
    var logger = (Logger) LoggerFactory.getLogger(DeadLetterConsumer.class);
    logger.addAppender(events);
    try {
      var consumer = new DeadLetterConsumer(new DeadLetterArchiver(repository, new ObjectMapper()));
      consumer.receive(dead("{\"customer\":\"fixture-private-data\"}"), channel);
    } finally {
      logger.detachAppender(events);
    }
    verify(channel).basicNack(7, false, true);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
    assertTrue(events.list.stream().noneMatch(e -> e.getFormattedMessage().contains("fixture-private-data")));
  }

  @Test
  void redeliveredMessageIsNotArchivedTwice() throws Exception {
    var message = dead("{\"foo\":1}");
    message.getMessageProperties().setMessageId(id);
    var already = new DeadLetter();
    already.id = id;
    already.dlq = "q.cmd.email.dlq";
    already.deathCount = 1L;
    when(repository.findById(id)).thenReturn(Optional.of(already));
    new DeadLetterConsumer(new DeadLetterArchiver(repository, new ObjectMapper())).receive(message, channel);
    verify(repository, never()).saveAndFlush(any());
    verify(channel).basicAck(7, false);
  }
}
