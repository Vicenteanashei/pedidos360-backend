package com.pedidos360.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
    @NotNull @Min(1) @Max(20) Long storeId,
    @NotBlank @Size(max = 120) String customerName,
    @NotBlank @Email @Size(max = 150) String customerEmail,
    @Size(max = 250) String deliveryAddress,
    @Size(max = 1000) String notes,
    @NotEmpty @Size(max = 100) @Valid List<@NotNull Item> items) {
  public record Item(
      @NotNull @Positive Long productId,
      @NotNull @Min(1) @Max(10000) Integer quantity,
      @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {}
}
