import { useState } from 'react';
import { useOrders } from './useOrders';
import OrderList from './OrderList';
import OrderDetail from './OrderDetail';
import OrderForm from './OrderForm';
import { STATUS, STATUS_KEYS } from '../constants/orderStatus';

export default function OrdersPage({ session, perms, onlyMine, openTarget }) {
  const [filters, setFilters] = useState({ status: '', from: '', to: '' });
  const { orders, loading, error, reload } = useOrders(filters, { onlyMine, email: session.username });
  // panel: { mode: 'detail', id } | { mode: 'new' } | { mode: 'edit', order } | null
  const [panel, setPanel] = useState(() => {
    if (openTarget === 'new' && perms.crearPedido) return { mode: 'new' };
    if (typeof openTarget === 'number') return { mode: 'detail', id: openTarget };
    return null;
  });

  const setFilter = (key) => (e) => setFilters((f) => ({ ...f, [key]: e.target.value }));

  async function afterSave(order) {
    await reload();
    setPanel({ mode: 'detail', id: order.id });
  }

  return (
    <div className="orders-page">
      <div className="page-head">
        <h2>{onlyMine ? 'Mis pedidos' : 'Pedidos'}</h2>
        {perms.crearPedido && (
          <button className="btn primary" onClick={() => setPanel({ mode: 'new' })}>+ Nuevo pedido</button>
        )}
      </div>

      <div className="filters card">
        <label>Estado
          <select value={filters.status} onChange={setFilter('status')}>
            <option value="">Todos</option>
            {STATUS_KEYS.map((s) => <option key={s} value={s}>{STATUS[s].label}</option>)}
          </select>
        </label>
        <label>Desde <input type="date" value={filters.from} onChange={setFilter('from')} /></label>
        <label>Hasta <input type="date" value={filters.to} onChange={setFilter('to')} /></label>
        <button className="btn ghost" onClick={() => setFilters({ status: '', from: '', to: '' })}>Limpiar</button>
        <button className="btn ghost" onClick={reload}>↻ Actualizar</button>
      </div>

      {error && <p className="error">{error}</p>}

      <div className="split">
        <div className="card list-card">
          {loading ? <p className="muted">Cargando…</p> : (
            <OrderList
              orders={orders}
              selectedId={panel?.id ?? panel?.order?.id}
              onSelect={(id) => setPanel({ mode: 'detail', id })}
              empty={onlyMine ? 'Todavía no tienes pedidos.' : 'No hay pedidos con estos filtros.'}
            />
          )}
        </div>

        <div className="card panel-card">
          {!panel && <p className="muted">Selecciona un pedido para ver el detalle.</p>}
          {panel?.mode === 'detail' && (
            <OrderDetail
              key={panel.id}
              id={panel.id}
              perms={perms}
              onEdit={(order) => setPanel({ mode: 'edit', order })}
              onChanged={reload}
              onDeleted={async () => { await reload(); setPanel(null); }}
            />
          )}
          {panel?.mode === 'new' && (
            <OrderForm session={session} onlyMine={onlyMine} onSaved={afterSave} onCancel={() => setPanel(null)} />
          )}
          {panel?.mode === 'edit' && (
            <OrderForm
              session={session}
              onlyMine={onlyMine}
              order={panel.order}
              onSaved={afterSave}
              onCancel={() => setPanel({ mode: 'detail', id: panel.order.id })}
            />
          )}
        </div>
      </div>
    </div>
  );
}
