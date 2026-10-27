package com.pedidos360.notify;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DeliveryTest {
  @Autowired DeliveryService delivery;
  @Autowired ReceiptRepository receipts;
  @Autowired ObjectMapper json;

  String envelope(String id, String type, boolean fail) {
    return "{\"eventId\":\""
        + id
        + "\",\"type\":\""
        + type
        + "\",\"ownerId\":\"t:u\",\"demoFailure\":"
        + fail
        + ",\"payload\":{\"id\":1,\"storeId\":1,\"status\":\"ENTREGADO\",\"total\":2200,\"customerEmail\":\"a@example.test\",\"items\":[]}}";
  }

  @BeforeEach
  void clean() {
    receipts.deleteAll();
  }

  @Test
  void duplicateDoesNotDuplicateReceipt() throws Exception {
    String id = UUID.randomUUID().toString();
    var event = json.readTree(envelope(id, "kitchen.ticket", false));
    delivery.process(event, "kitchen.ticket");
    delivery.process(event, "kitchen.ticket");
    assertEquals(1, receipts.count());
    assertTrue(
        Files.readString(delivery.directory().resolve(id + ".txt")).contains("TICKET COCINA"));
  }

  @Test
  void forcedFailurePropagatesForBrokerRetry() throws Exception {
    var event = json.readTree(envelope(UUID.randomUUID().toString(), "email.send", true));
    assertThrows(IllegalStateException.class, () -> delivery.process(event, "email.send"));
    assertEquals(0, receipts.count());
  }

  @Test
  void invoiceContainsNonFiscalDisclaimer() throws Exception {
    String id = UUID.randomUUID().toString();
    delivery.process(json.readTree(envelope(id, "invoice.gen", false)), "invoice.gen");
    byte[] bytes = Files.readAllBytes(delivery.directory().resolve(id + ".pdf"));
    assertTrue(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
      assertTrue(text.contains("sin validez tributaria"));
      assertTrue(text.contains("Pedido #1"));
      assertTrue(text.contains("2.200"));
    }
    delivery.process(json.readTree(envelope(id, "invoice.gen", false)), "invoice.gen");
    assertEquals(1, receipts.count());
    assertArrayEquals(bytes, Files.readAllBytes(delivery.directory().resolve(id + ".pdf")));
  }
}
