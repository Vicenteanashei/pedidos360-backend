package com.pedidos360.orders.dto;

import com.pedidos360.orders.model.CustomerOrder;
import com.pedidos360.orders.model.OrderItem;
import com.pedidos360.orders.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
		Long id,
		Long storeId,
		String customerName,
		String customerEmail,
		String deliveryAddress,
		String notes,
		OrderStatus status,
		BigDecimal total,
		List<Item> items,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime acceptedAt,
		LocalDateTime dispatchedAt,
		LocalDateTime deliveredAt
) {
	public record Item(Long id, Long productId, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
		static Item from(OrderItem i) {
			return new Item(i.getId(), i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.subtotal());
		}
	}

	public static OrderResponse from(CustomerOrder o) {
		return new OrderResponse(
				o.getId(), o.getStoreId(), o.getCustomerName(), o.getCustomerEmail(),
				o.getDeliveryAddress(), o.getNotes(), o.getStatus(), o.getTotal(),
				o.getItems().stream().map(Item::from).toList(),
				o.getCreatedAt(), o.getUpdatedAt(), o.getAcceptedAt(), o.getDispatchedAt(), o.getDeliveredAt());
	}
}
