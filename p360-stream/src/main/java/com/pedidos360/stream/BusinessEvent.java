package com.pedidos360.stream;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record BusinessEvent(
    @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String eventId,
    @NotBlank String type,
    @NotBlank String action,
    @NotNull Instant timestamp,
    @NotBlank @Size(max = 128) String correlationId,
    @NotBlank @Size(max = 250) String actorId,
    @NotBlank @Size(max = 80) String source,
    String previousStatus,
    @NotNull @Valid Snapshot payload) {
  public static final String ORDERS = "orders.events";
  public static final String TIMELINE = "audit.timeline";
  public static final Set<String> STATUSES = Set.of(
      "CREADO", "ACEPTADO", "EN_PREPARACION", "DESPACHADO", "ENTREGADO", "CANCELADO");
  public static final Set<String> TYPES = Set.of("OrderCreated", "OrderUpdated", "OrderAccepted",
      "OrderPreparing", "OrderDispatched", "OrderDelivered", "OrderCancelled", "OrderDeleted", "OrderSnapshot");

  public record Snapshot(
      @NotNull @Positive Long id,
      @Positive long version,
      @NotNull @Min(1) @Max(20) Long storeId,
      @NotBlank String status,
      boolean deleted,
      @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal total,
      @NotNull Instant createdAt,
      Instant deliveredAt,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> items) {}

  public record Line(
      @NotNull @Positive Long productId,
      @Min(1) @Max(10000) int quantity,
      @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {}

  public void validate(jakarta.validation.Validator validator) {
    var violations = validator.validate(this);
    if (!violations.isEmpty()) throw new IllegalArgumentException("Envelope Kafka inválido");
    java.util.UUID.fromString(eventId);
    if (!TYPES.contains(type) || !STATUSES.contains(payload.status()))
      throw new IllegalArgumentException("Tipo o estado no soportado");
    if (!Set.of("CREATED", "UPDATED", "STATUS_CHANGED", "DELETED", "SNAPSHOT").contains(action))
      throw new IllegalArgumentException("Acción no soportada");
    String expected = switch (action) {
      case "CREATED" -> "OrderCreated";
      case "UPDATED" -> "OrderUpdated";
      case "DELETED" -> "OrderDeleted";
      case "SNAPSHOT" -> "OrderSnapshot";
      default -> switch (payload.status()) {
        case "ACEPTADO" -> "OrderAccepted";
        case "EN_PREPARACION" -> "OrderPreparing";
        case "DESPACHADO" -> "OrderDispatched";
        case "ENTREGADO" -> "OrderDelivered";
        case "CANCELADO" -> "OrderCancelled";
        default -> throw new IllegalArgumentException("Transición no soportada");
      };
    };
    if (!expected.equals(type) || (!action.equals("SNAPSHOT") && payload.deleted() != action.equals("DELETED")))
      throw new IllegalArgumentException("Evento incompatible con su snapshot");
    BigDecimal sum = payload.items().stream()
        .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (sum.compareTo(payload.total()) != 0)
      throw new IllegalArgumentException("Total incompatible");
    if ("ENTREGADO".equals(payload.status()) &&
        (payload.deliveredAt() == null || payload.deliveredAt().isBefore(payload.createdAt())))
      throw new IllegalArgumentException("Fechas de entrega inválidas");
  }
}

