package com.pedidos360.notify;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Academic receipt, generated locally. No external HTML renderer or fiscal integration. */
final class InvoicePdf implements AutoCloseable {
  private final PDDocument document = new PDDocument();
  private final PDType0Font font;
  private final JsonNode payload;
  private final NumberFormat money = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL"));
  private PDPageContentStream stream;
  private float y;
  private int page;
  private static final float LEFT = 44, RIGHT = 550, BOTTOM = 72;

  private InvoicePdf(JsonNode payload) throws IOException {
    this.payload = payload;
    try (var input = InvoicePdf.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
      if (input == null) throw new IOException("Fuente del comprobante ausente");
      font = PDType0Font.load(document, input);
    }
    money.setMaximumFractionDigits(2);
    document.getDocumentInformation().setTitle("Comprobante académico Pedidos360");
    document.getDocumentInformation().setAuthor("Pedidos360");
  }

  static byte[] create(JsonNode payload) throws IOException {
    try (var pdf = new InvoicePdf(payload);
        var output = new ByteArrayOutputStream()) {
      pdf.render();
      pdf.finishPage();
      pdf.document.save(output);
      return output.toByteArray();
    }
  }

  private void render() throws IOException {
    newPage();
    paragraph("Cliente: " + payload.path("customerName").asText("Cliente de prueba"));
    paragraph("Correo: " + payload.path("customerEmail").asText());
    if (!payload.path("deliveryAddress").asText().isBlank())
      paragraph("Dirección: " + payload.path("deliveryAddress").asText());
    y -= 8;
    tableHeader();
    for (var item : payload.path("items")) {
      if (y < BOTTOM + 24) {
        newPage();
        tableHeader();
      }
      int qty = item.path("quantity").asInt();
      BigDecimal price = item.path("unitPrice").decimalValue();
      text("Producto #" + item.path("productId").asText(), LEFT, y, 10);
      text(Integer.toString(qty), 274, y, 10);
      rightText(currency(price), 420, y, 10);
      rightText(currency(price.multiply(BigDecimal.valueOf(qty))), RIGHT, y, 10);
      y -= 22;
    }
    if (y < BOTTOM + 54) newPage();
    line(y + 6);
    y -= 20;
    text("TOTAL CLP", LEFT, y, 13);
    rightText(currency(payload.path("total").decimalValue()), RIGHT, y, 15);
    y -= 30;
    if (!payload.path("notes").asText().isBlank())
      paragraph("Notas: " + payload.path("notes").asText());
  }

  private String currency(BigDecimal value) {
    return "$ " + money.format(value);
  }

  private void tableHeader() throws IOException {
    if (y < BOTTOM + 48) newPage();
    text("Producto", LEFT, y, 11);
    text("Cantidad", 260, y, 11);
    rightText("Precio CLP", 420, y, 11);
    rightText("Subtotal CLP", RIGHT, y, 11);
    y -= 10;
    line(y);
    y -= 20;
  }

  private void newPage() throws IOException {
    finishPage();
    var sheet = new PDPage(PDRectangle.A4);
    document.addPage(sheet);
    page++;
    stream = new PDPageContentStream(document, sheet);
    stream.setNonStrokingColor(new Color(27, 73, 101));
    text("PEDIDOS360", LEFT, 790, 22);
    stream.setNonStrokingColor(Color.BLACK);
    text("Comprobante académico", LEFT, 765, 14);
    text(
        "Pedido #"
            + payload.path("id").asLong()
            + "   |   Local "
            + payload.path("storeId").asLong(),
        LEFT,
        742,
        11);
    text("Estado: " + payload.path("status").asText(), LEFT, 723, 11);
    line(709);
    y = 689;
  }

  private void finishPage() throws IOException {
    if (stream == null) return;
    text("Comprobante académico, sin validez tributaria.", LEFT, 44, 10);
    rightText("Página " + page, RIGHT, 44, 10);
    stream.close();
    stream = null;
  }

  private void line(float at) throws IOException {
    stream.setStrokingColor(new Color(210, 220, 225));
    stream.moveTo(LEFT, at);
    stream.lineTo(RIGHT, at);
    stream.stroke();
  }

  private void paragraph(String value) throws IOException {
    StringBuilder row = new StringBuilder();
    for (String word : printable(value).split("\\s+")) {
      String candidate = row.length() == 0 ? word : row + " " + word;
      if (font.getStringWidth(candidate) / 1000 * 11 <= RIGHT - LEFT) {
        row.setLength(0);
        row.append(candidate);
        continue;
      }
      if (row.length() > 0) {
        paragraphLine(row.toString());
        row.setLength(0);
      }
      for (int cp : word.codePoints().toArray()) {
        String next = row.toString() + new String(Character.toChars(cp));
        if (font.getStringWidth(next) / 1000 * 11 > RIGHT - LEFT) {
          paragraphLine(row.toString());
          row.setLength(0);
        }
        row.appendCodePoint(cp);
      }
    }
    if (row.length() > 0) paragraphLine(row.toString());
  }

  private void paragraphLine(String value) throws IOException {
    if (y < BOTTOM + 18) newPage();
    text(value, LEFT, y, 11);
    y -= 17;
  }

  private String printable(String value) throws IOException {
    var result = new StringBuilder();
    for (int cp : value.codePoints().toArray()) {
      if (Character.isISOControl(cp)) {
        result.append(' ');
        continue;
      }
      result.appendCodePoint(font.hasGlyph(cp) ? cp : '?');
    }
    return result.toString();
  }

  private void text(String value, float x, float at, float size) throws IOException {
    stream.beginText();
    stream.setFont(font, size);
    stream.newLineAtOffset(x, at);
    stream.showText(printable(value));
    stream.endText();
  }

  private void rightText(String value, float x, float at, float size) throws IOException {
    String safe = printable(value);
    text(safe, x - font.getStringWidth(safe) / 1000 * size, at, size);
  }

  @Override
  public void close() throws IOException {
    try {
      if (stream != null) stream.close();
    } finally {
      document.close();
    }
  }
}
