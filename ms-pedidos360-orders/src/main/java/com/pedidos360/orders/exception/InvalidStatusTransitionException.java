package com.pedidos360.orders.exception;

/** Regla de negocio violada: transicion de estado no permitida o edicion de un pedido ya procesado. */
public class InvalidStatusTransitionException extends RuntimeException {
	public InvalidStatusTransitionException(String message) {
		super(message);
	}
}
