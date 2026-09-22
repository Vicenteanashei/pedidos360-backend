package com.pedidos360.orders.model;

import java.util.Set;

public enum OrderStatus {
	CREADO,
	ACEPTADO,
	EN_PREPARACION,
	DESPACHADO,
	ENTREGADO,
	CANCELADO;

	/**
	 * Flujo: CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO.
	 * Se puede CANCELAR antes de entregar. Regla del caso: no se puede despachar sin aceptar.
	 */
	public Set<OrderStatus> allowedTransitions() {
		return switch (this) {
			case CREADO -> Set.of(ACEPTADO, CANCELADO);
			case ACEPTADO -> Set.of(EN_PREPARACION, CANCELADO);
			case EN_PREPARACION -> Set.of(DESPACHADO, CANCELADO);
			case DESPACHADO -> Set.of(ENTREGADO, CANCELADO);
			case ENTREGADO, CANCELADO -> Set.of();
		};
	}

	public boolean canTransitionTo(OrderStatus target) {
		return allowedTransitions().contains(target);
	}
}
