package com.pedidos360.notify;

import static com.pedidos360.common.MessagingConfig.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {
  private final ReceiptRepository receipts;
  private final Path directory;
  private final boolean demoFailure;
  private final org.springframework.beans.factory.ObjectProvider<EmailSender> sender;

  public DeliveryService(
      ReceiptRepository r,
      @Value("${app.artifacts}") String d,
      @Value("${app.demo-failure-enabled:false}") boolean demo,
      org.springframework.beans.factory.ObjectProvider<EmailSender> sender) {
    receipts = r;
    directory = Path.of(d).toAbsolutePath();
    demoFailure = demo;
    this.sender = sender;
  }

  @Transactional
  public void process(JsonNode event, String expected) throws Exception {
    String id = event.path("eventId").asText();
    String type = event.path("type").asText();
    if (!id.matches("[0-9a-fA-F-]{36}") || !type.equals(expected))
      throw new IllegalArgumentException("Envelope incompatible");
    if (receipts.existsById(id)) return;
    if (demoFailure && event.path("demoFailure").asBoolean())
      throw new DemoDeliveryFailureException();
    var payload = event.path("payload");
    String owner = event.path("ownerId").asText();
    if (owner.isBlank() || payload.path("id").asLong() <= 0)
      throw new IllegalArgumentException("Pedido/propietario ausentes");
    String file =
        id + (type.equals(EMAIL_KEY) ? ".eml" : type.equals(INVOICE_KEY) ? ".pdf" : ".txt");
    String details =
        "Pedido #"
            + payload.path("id").asLong()
            + " · Local "
            + payload.path("storeId").asLong()
            + " · Estado "
            + payload.path("status").asText()
            + " · Total CLP "
            + payload.path("total").asText();
    String content =
        switch (type) {
          case EMAIL_KEY ->
              "To: "
                  + clean(payload.path("customerEmail").asText())
                  + "\r\nSubject: Actualización Pedidos360\r\nMessage-ID: <"
                  + id
                  + "@pedidos360.local>\r\n"
                  + "MIME-Version: 1.0\r\n"
                  + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                  + details;
          case KITCHEN_KEY ->
              "TICKET COCINA\n"
                  + details
                  + "\n"
                  + payload.path("items").toPrettyString()
                  + "\nNotas: "
                  + payload.path("notes").asText();
          case INVOICE_KEY -> ""; // Binary PDF is generated below.
          default -> throw new IllegalArgumentException("Tipo desconocido");
        };
    Files.createDirectories(directory);
    Path target = directory.resolve(file),
        temp = Files.createTempFile(directory, "delivery-", ".tmp");
    try {
      Files.write(
          temp,
          type.equals(INVOICE_KEY)
              ? InvoicePdf.create(payload)
              : content.getBytes(StandardCharsets.UTF_8));
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
    if (type.equals(EMAIL_KEY) && sender.getIfAvailable() != null)
      sender.getObject().send(target);
    // Same event always produces the same artifact. DB+file crash/retry cannot create a second
    // document.
    var receipt = new Receipt();
    receipt.eventId = id;
    receipt.ownerId = owner;
    receipt.type = type;
    receipt.orderId = payload.path("id").asLong();
    receipt.storeId = payload.path("storeId").asLong();
    receipt.processedAt = Instant.now();
    receipt.artifact = file;
    receipts.saveAndFlush(receipt);
  }

  private String clean(String s) {
    return s.replace("\r", "").replace("\n", "");
  }

  public Path directory() {
    return directory;
  }
}
