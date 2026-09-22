package com.pedidos360.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
		@NotNull Long storeId,
		@NotBlank @Size(max = 120) String customerName,
		@NotBlank @Email @Size(max = 150) String customerEmail,
		@Size(max = 250) String deliveryAddress,
		@Size(max = 1000) String notes,
		@NotEmpty @Valid List<Item> items
) {
	public record Item(
			@NotNull Long productId,
			@NotNull @Positive Integer quantity,
			@NotNull @PositiveOrZero BigDecimal unitPrice
	) {
	}
}
