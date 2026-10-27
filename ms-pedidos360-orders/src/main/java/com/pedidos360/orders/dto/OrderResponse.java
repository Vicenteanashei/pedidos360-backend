package com.pedidos360.orders.dto;

import com.pedidos360.orders.model.CustomerOrder;
import com.pedidos360.orders.model.OrderItem;
import com.pedidos360.orders.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime dispatchedAt,
    OffsetDateTime deliveredAt) {
  public record Item(
      Long id, Long productId, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    static Item from(OrderItem i) {
      return new Item(i.getId(), i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.subtotal());
    }
  }

  private static OffsetDateTime utc(LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  public static OrderResponse from(CustomerOrder o) {
    return new OrderResponse(
        o.getId(),
        o.getStoreId(),
        o.getCustomerName(),
        o.getCustomerEmail(),
        o.getDeliveryAddress(),
        o.getNotes(),
        o.getStatus(),
        o.getTotal(),
        o.getItems().stream().map(Item::from).toList(),
        utc(o.getCreatedAt()),
        utc(o.getUpdatedAt()),
        utc(o.getAcceptedAt()),
        utc(o.getDispatchedAt()),
        utc(o.getDeliveredAt()));
  }
}
