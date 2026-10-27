package com.pedidos360.notify.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.common.MessagingConfig.Task;
import com.pedidos360.notify.DeliveryService;
import com.pedidos360.notify.DemoDeliveryFailureException;
import com.pedidos360.notify.config.ConsumerPolicy;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class TaskMessageProcessorTest {
  DeliveryService delivery;
  Channel channel;
  TaskMessageProcessor processor;
  final String id = "d185cebc-1345-4e08-b921-8eb9241d578a";

  @BeforeEach
  void setup() {
    delivery = mock(DeliveryService.class);
    channel = mock(Channel.class);
    processor = new TaskMessageProcessor(delivery, new ObjectMapper(), new ConsumerPolicy(3, 0, 0));
  }

  Message message(String body) {
    var properties = new MessageProperties();
    properties.setDeliveryTag(42);
    return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
  }

  Message valid() { return message("{\"eventId\":\"" + id + "\",\"customer\":\"fixture-private-data\"}"); }

  @Test
  void acknowledgesOnlyAfterBusinessTransactionReturns() throws Exception {
    processor.consume(valid(), channel, Task.EMAIL);
    var order = inOrder(delivery, channel);
    order.verify(delivery).process(any(), eq(Task.EMAIL.routingKey()));
    order.verify(channel).basicAck(42, false);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  @Test
  void transientErrorsRetryThenAcknowledgeOnce() throws Exception {
    doThrow(new DemoDeliveryFailureException()).doThrow(new IOException("fixture-private-data"))
        .doNothing().when(delivery).process(any(), anyString());
    processor.consume(valid(), channel, Task.KITCHEN);
    verify(delivery, times(3)).process(any(), eq(Task.KITCHEN.routingKey()));
    verify(channel, times(1)).basicAck(42, false);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  @Test
  void permanentErrorGoesDirectlyToDlq() throws Exception {
    doThrow(new IllegalArgumentException("fixture-private-data")).when(delivery).process(any(), anyString());
    processor.consume(valid(), channel, Task.INVOICE);
    verify(delivery, times(1)).process(any(), eq(Task.INVOICE.routingKey()));
    verify(channel).basicNack(42, false, false);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void malformedJsonIsRejectedBeforeBusinessAndWithoutRequeue() throws Exception {
    processor.consume(message("malformed fixture-private-data"), channel, Task.EMAIL);
    verifyNoInteractions(delivery);
    verify(channel).basicNack(42, false, false);
  }

  @Test
  void exhaustedRetriesLogOnlySafeMetadataAndDeadLetterOnce() throws Exception {
    doThrow(new IOException("fixture-private-data")).when(delivery).process(any(), anyString());
    var logger = (Logger) LoggerFactory.getLogger(TaskMessageProcessor.class);
    var events = new ListAppender<ILoggingEvent>(); events.start(); logger.addAppender(events);
    try {
      processor.consume(valid(), channel, Task.EMAIL);
      verify(delivery, times(3)).process(any(), anyString());
      verify(channel, times(1)).basicNack(42, false, false);
      verify(channel, never()).basicAck(anyLong(), anyBoolean());
      String output = events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertTrue(output.contains("NACK_DLQ")); assertTrue(output.contains(Task.EMAIL.dlq()));
      assertTrue(output.contains(id)); assertFalse(output.contains("fixture-private-data"));
      assertTrue(events.list.stream().allMatch(e -> e.getThrowableProxy() == null));
    } finally { logger.detachAppender(events); events.stop(); }
  }

  @Test
  void lostAckDoesNotDeadLetterAnAlreadyProcessedMessage() throws Exception {
    doThrow(new IOException("channel lost")).when(channel).basicAck(42, false);
    assertThrows(IOException.class, () -> processor.consume(valid(), channel, Task.EMAIL));
    verify(delivery, times(1)).process(any(), anyString());
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  @Test
  void failedNackPropagatesForChannelRecovery() throws Exception {
    doThrow(new IOException("channel lost")).when(channel).basicNack(42, false, false);
    assertThrows(IOException.class, () -> processor.consume(message("invalid"), channel, Task.EMAIL));
    verifyNoInteractions(delivery);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void shutdownDuringBackoffPreservesMessageForRedelivery() throws Exception {
    processor = new TaskMessageProcessor(delivery, new ObjectMapper(), new ConsumerPolicy(3, 1000, 4000));
    doThrow(new DemoDeliveryFailureException()).when(delivery).process(any(), anyString());
    Thread.currentThread().interrupt();
    try {
      processor.consume(valid(), channel, Task.EMAIL);
      assertTrue(Thread.currentThread().isInterrupted());
      verify(delivery, times(1)).process(any(), anyString());
      verify(channel).basicNack(42, false, true);
      verify(channel, never()).basicAck(anyLong(), anyBoolean());
    } finally { Thread.interrupted(); }
  }
}
