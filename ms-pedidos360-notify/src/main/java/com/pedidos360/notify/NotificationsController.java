package com.pedidos360.notify;

import com.pedidos360.common.Actor;
import java.nio.file.*;
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
  private final DeadLetterRepository deadLetters;

  public NotificationsController(ReceiptRepository r, DeliveryService s, DeadLetterRepository d) {
    receipts = r;
    service = s;
    deadLetters = d;
  }

  /** Mensajes que llegaron a las DLQ y archivó el consumidor de errores, más recientes primero. */
  @GetMapping("/dead-letters")
  @PreAuthorize("hasRole('Admin')")
  public List<DeadLetter> deadLetters() {
    return deadLetters.findAllByOrderByArchivedAtDesc(PageRequest.of(0, 100));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('Admin','Cliente')")
  public List<Receipt>
      list() { // Filter in DB before paginating, so unrelated owners cannot suppress own results.
    return Actor.has("Admin")
        ? receipts
            .findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "processedAt")))
            .getContent()
        : receipts.findByOwnerIdOrderByProcessedAtDesc(Actor.id(), PageRequest.of(0, 100));
  }

  @GetMapping("/{id}/artifact")
  @PreAuthorize("hasAnyRole('Admin','Cliente')")
  public ResponseEntity<byte[]> artifact(@PathVariable String id) throws Exception {
    var r =
        receipts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!Actor.has("Admin") && !r.ownerId.equals(Actor.id()))
      throw new AccessDeniedException("Documento ajeno");
    Path file = service.directory().resolve(r.artifact).normalize();
    if (!file.getParent().equals(service.directory()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return ResponseEntity.ok()
        .contentType(
            r.artifact.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"" + r.artifact + "\"")
        .body(Files.readAllBytes(file));
  }
}
