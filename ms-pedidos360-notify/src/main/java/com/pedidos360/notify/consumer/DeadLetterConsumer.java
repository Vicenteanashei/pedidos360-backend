package com.pedidos360.notify.consumer;

import com.pedidos360.common.MessagingConfig;
import com.pedidos360.notify.DeadLetterArchiver;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumidor de las tres DLQ: registra en el log y archiva en la base cada mensaje muerto, y solo
 * después confirma (ACK). Si el archivo falla, lo reencola para no perder la evidencia.
 * Para mostrar mensajes acumulados en las DLQ durante una demo: DLQ_LISTENER_ENABLED=false.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.dlq-listener.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterConsumer {
  private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);
  static final long REQUEUE_DELAY_MS = 2000;
  private final DeadLetterArchiver archiver;

  public DeadLetterConsumer(DeadLetterArchiver archiver) {
    this.archiver = archiver;
  }

  @RabbitListener(
      queues = {MessagingConfig.EMAIL_DLQ, MessagingConfig.KITCHEN_DLQ, MessagingConfig.INVOICE_DLQ},
      ackMode = "MANUAL")
  public void receive(Message message, Channel channel) throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    try {
      var entry = archiver.archive(message);
      channel.basicAck(tag, false);
      // Solo identificadores: nunca el cuerpo, datos del cliente ni credenciales.
      log.warn("Rabbit DLQ_ARCHIVADA dlq={} origen={} razon={} routingKey={} eventId={} type={} muertes={}",
          entry.dlq, entry.originalQueue, entry.reason, entry.originalRoutingKey,
          entry.eventId == null ? "no-disponible" : entry.eventId,
          entry.type == null ? "no-disponible" : entry.type, entry.deathCount);
    } catch (Exception error) {
      log.error("Rabbit DLQ_REENCOLADA dlq={} error={}",
          message.getMessageProperties().getConsumerQueue(), error.getClass().getSimpleName());
      try {
        Thread.sleep(REQUEUE_DELAY_MS); // evita un bucle rápido si la base no está disponible
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      channel.basicNack(tag, false, true);
    }
  }
}
