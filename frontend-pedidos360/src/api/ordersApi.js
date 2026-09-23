import { request } from './http';

export function listOrders({ status, from, to } = {}) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (from) params.set('from', `${from}T00:00:00`);
  if (to) params.set('to', `${to}T23:59:59`);
  const qs = params.toString();
  return request(`/api/orders${qs ? `?${qs}` : ''}`);
}

export const getOrder = (id) => request(`/api/orders/${id}`);
export const createOrder = (order) => request('/api/orders', { method: 'POST', body: order });
export const updateOrder = (id, order) => request(`/api/orders/${id}`, { method: 'PUT', body: order });
export const changeStatus = (id, status) => request(`/api/orders/${id}/status`, { method: 'PUT', body: { status } });
export const deleteOrder = (id) => request(`/api/orders/${id}`, { method: 'DELETE' });
