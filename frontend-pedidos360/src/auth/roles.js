// Mismas reglas que SecurityConfig del BFF. La UI solo muestra u oculta acciones:
// quien realmente autoriza es el backend (si alguien fuerza la llamada, el BFF responde 403).
export const ROLES = ['Admin', 'Operador', 'Cliente'];

const PERMISSIONS = {
  verPedidos: ['Admin', 'Operador', 'Cliente'],
  crearPedido: ['Operador', 'Cliente'],
  editarPedido: ['Operador', 'Cliente'],
  cambiarEstado: ['Admin', 'Operador'],
  eliminarPedido: ['Admin'],
};

export function can(roles, permission) {
  return (PERMISSIONS[permission] ?? []).some((role) => roles.includes(role));
}

export const ROLE_DESCRIPTIONS = {
  Admin: 'Ve todos los pedidos, cambia estados y puede eliminar.',
  Operador: 'Crea pedidos, los edita y los mueve por el flujo (aceptar, preparar, despachar, entregar).',
  Cliente: 'Crea sus pedidos, los edita mientras no hayan sido aceptados y sigue su estado.',
};
