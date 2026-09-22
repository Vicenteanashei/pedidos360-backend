package com.pedidos360.orders.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Pedido de un cliente a una PyME (panaderia/cafe). "Order" es palabra reservada en SQL/JPQL. */
@Entity
@Table(name = "P360_ORDERS")
@Getter
@Setter
@NoArgsConstructor
public class CustomerOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** PyME (local) que recibe el pedido. */
	@Column(nullable = false)
	private Long storeId;

	@Column(nullable = false, length = 120)
	private String customerName;

	@Column(nullable = false, length = 150)
	private String customerEmail;

	@Column(length = 250)
	private String deliveryAddress;

	@Column(length = 1000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrderStatus status;

	@Column(precision = 12, scale = 2)
	private BigDecimal total = BigDecimal.ZERO;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<OrderItem> items = new ArrayList<>();

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;
	private LocalDateTime acceptedAt;
	private LocalDateTime dispatchedAt;
	/** createdAt -> deliveredAt = lead time (KPI de reporteria). */
	private LocalDateTime deliveredAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
		if (status == null) {
			status = OrderStatus.CREADO;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public void replaceItems(List<OrderItem> newItems) {
		items.clear();
		newItems.forEach(item -> {
			item.setOrder(this);
			items.add(item);
		});
		total = items.stream()
				.map(OrderItem::subtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}
