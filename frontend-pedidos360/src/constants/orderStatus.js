// Replica las transiciones de OrderStatus.java (la regla real la aplica ms-pedidos360-orders)
export const STATUS = {
  CREADO: { label: 'Creado', color: 'gray' },
  ACEPTADO: { label: 'Aceptado', color: 'blue' },
  EN_PREPARACION: { label: 'En preparación', color: 'amber' },
  DESPACHADO: { label: 'Despachado', color: 'purple' },
  ENTREGADO: { label: 'Entregado', color: 'green' },
  CANCELADO: { label: 'Cancelado', color: 'red' },
};

export const STATUS_KEYS = Object.keys(STATUS);

export const TRANSITIONS = {
  CREADO: ['ACEPTADO', 'CANCELADO'],
  ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['DESPACHADO', 'CANCELADO'],
  DESPACHADO: ['ENTREGADO', 'CANCELADO'],
  ENTREGADO: [],
  CANCELADO: [],
};

// Texto del boton para avanzar a cada estado
export const ACTION_LABEL = {
  ACEPTADO: 'Aceptar',
  EN_PREPARACION: 'Pasar a preparación',
  DESPACHADO: 'Despachar',
  ENTREGADO: 'Marcar entregado',
  CANCELADO: 'Cancelar pedido',
};

export const ACTIVE_STATUSES = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO'];
