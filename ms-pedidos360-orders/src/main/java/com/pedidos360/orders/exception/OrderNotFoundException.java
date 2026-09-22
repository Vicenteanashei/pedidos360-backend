package com.pedidos360.orders.exception;

public class OrderNotFoundException extends RuntimeException {
	public OrderNotFoundException(Long id) {
		super("Pedido " + id + " no encontrado");
	}
}
