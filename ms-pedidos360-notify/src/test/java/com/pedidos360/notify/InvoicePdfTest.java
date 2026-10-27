package com.pedidos360.notify;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class InvoicePdfTest {
  @Test
  void longReceiptPaginatesAndPreservesLastItemAndUnicode() throws Exception {
    var json = new ObjectMapper();
    var payload =
        json.createObjectNode()
            .put("id", 43)
            .put("storeId", 1)
            .put("status", "ENTREGADO")
            .put("customerName", "María Muñoz 🥐")
            .put("customerEmail", "manual@example.test")
            .put("total", 250000);
    var items = payload.putArray("items");
    for (int n = 1; n <= 100; n++)
      items.addObject().put("productId", n).put("quantity", 1).put("unitPrice", 2500);
    payload.put("notes", "Notas de cocina: " + "panadería ".repeat(90));
    byte[] bytes = InvoicePdf.create(payload);
    try (var pdf = Loader.loadPDF(bytes)) {
      assertTrue(pdf.getNumberOfPages() > 1);
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("María Muñoz"));
      assertTrue(text.contains("Producto #100"));
      assertTrue(text.contains("250.000"));
      assertTrue(text.contains("Notas de cocina"));
      for (var page : pdf.getPages())
        assertFalse(page.getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.AA));
      assertNull(pdf.getDocumentCatalog().getOpenAction());
    }
    saveSample("comprobante-multipagina.pdf", bytes);
    items.removeAll();
    items.addObject().put("productId", 41).put("quantity", 2).put("unitPrice", 2500);
    payload
        .put("customerName", "María Muñoz")
        .put("total", 5000)
        .put("notes", "Pedido de demostración; datos ficticios.");
    saveSample("comprobante-demostracion.pdf", InvoicePdf.create(payload));
  }

  private void saveSample(String name, byte[] data) throws Exception {
    String dir = System.getProperty("pdf.sample.dir");
    if (dir != null) {
      Files.createDirectories(Path.of(dir));
      Files.write(Path.of(dir, name), data);
    }
  }
}
