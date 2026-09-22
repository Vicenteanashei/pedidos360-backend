package com.pedidos360.orders.dto;

import com.pedidos360.orders.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull OrderStatus status) {
}
