package com.pedidos360.notify.consumer;

import com.pedidos360.common.MessagingConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class KitchenConsumer {
  private final TaskMessageProcessor processor;

  public KitchenConsumer(TaskMessageProcessor processor) { this.processor = processor; }

  @RabbitListener(queues = MessagingConfig.KITCHEN_QUEUE, ackMode = "MANUAL")
  public void receive(Message message, Channel channel) throws IOException {
    processor.consume(message, channel, MessagingConfig.Task.KITCHEN);
  }
}
