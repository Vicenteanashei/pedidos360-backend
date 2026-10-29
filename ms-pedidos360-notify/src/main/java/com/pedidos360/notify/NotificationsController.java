package com.pedidos360.notify;

import static com.pedidos360.common.MessagingConfig.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pedidos360.common.Actor;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {
  private final ReceiptRepository receipts;
  private final DeliveryService service;

  public NotificationsController(ReceiptRepository r, DeliveryService s) {
    receipts = r;
    service = s;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('Admin','Cliente')")
  public List<Notification>
      list() { // Filter in DB before paginating, so unrelated owners cannot suppress own results.
    boolean admin = Actor.has("Admin");
    List<Receipt> rows = admin
        ? receipts
            .findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "processedAt")))
            .getContent()
        : receipts.findByOwnerIdOrderByProcessedAtDesc(Actor.id(), PageRequest.of(0, 100));
    return rows.stream().map(r -> notification(r, admin)).toList();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Notification(
      String eventId, Long orderId, Long storeId, Instant processedAt,
      String message, boolean downloadable, String artifact, String type, String ownerId) {}

  private boolean invoicePdf(Receipt r) {
    return INVOICE_KEY.equals(r.type) && r.artifact != null && r.artifact.endsWith(".pdf");
  }

  private String customerMessage(Receipt r) {
    return switch (r.type) {
      case EMAIL_KEY -> "Hay una actualización de tu pedido. Puedes revisar su detalle en Pedidos.";
      case KITCHEN_KEY -> "Tu pedido fue aceptado para preparación.";
      case INVOICE_KEY -> invoicePdf(r)
          ? "El comprobante PDF de tu pedido está disponible."
          : "Se registró el comprobante de tu pedido.";
      default -> "Se registró una actualización de tu pedido.";
    };
  }

  private Notification notification(Receipt r, boolean admin) {
    boolean downloadable = admin || invoicePdf(r);
    return new Notification(r.eventId, r.orderId, r.storeId, r.processedAt,
        customerMessage(r), downloadable,
        admin ? r.artifact : downloadable ? "comprobante-pedido-" + r.orderId + ".pdf" : null,
        admin ? r.type : null, admin ? r.ownerId : null);
  }

  @GetMapping("/{id}/artifact")
  @PreAuthorize("hasAnyRole('Admin','Cliente')")
  public ResponseEntity<byte[]> artifact(@PathVariable String id) throws Exception {
    var r =
        receipts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!Actor.has("Admin") && !r.ownerId.equals(Actor.id()))
      throw new AccessDeniedException("Documento ajeno");
    if (!Actor.has("Admin") && !invoicePdf(r))
      throw new AccessDeniedException("Solo puedes descargar el comprobante PDF de tu pedido");
    Path directory = service.directory().toAbsolutePath().normalize();
    Path file = directory.resolve(r.artifact).normalize();
    if (!file.getParent().equals(directory))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    if (!Files.isRegularFile(file))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no disponible");
    String filename = Actor.has("Admin") ? r.artifact : "comprobante-pedido-" + r.orderId + ".pdf";
    return ResponseEntity.ok()
        .contentType(
            r.artifact.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .cacheControl(CacheControl.noStore())
        .body(Files.readAllBytes(file));
  }
}
