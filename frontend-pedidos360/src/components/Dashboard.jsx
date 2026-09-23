import { useState } from 'react';
import { useOrders } from './useOrders';
import { changeStatus } from '../api/ordersApi';
import { ACTIVE_STATUSES, STATUS, STATUS_KEYS } from '../constants/orderStatus';
import { storeName } from '../constants/catalog';
import { date, formatDuration, money } from '../utils/format';
import StatusBadge from './StatusBadge';

function Kpi({ label, value, hint }) {
  return (
    <div className="kpi">
      <div className="kpi-value">{value}</div>
      <div className="kpi-label">{label}</div>
      {hint && <div className="kpi-hint">{hint}</div>}
    </div>
  );
}

function AdminView({ orders }) {
  const delivered = orders.filter((o) => o.status === 'ENTREGADO');
  const active = orders.filter((o) => ACTIVE_STATUSES.includes(o.status));
  const sales = delivered.reduce((sum, o) => sum + Number(o.total), 0);
  const leadTimes = delivered.map((o) => new Date(o.deliveredAt) - new Date(o.createdAt));
  const avgLead = leadTimes.length ? formatDuration(leadTimes.reduce((a, b) => a + b, 0) / leadTimes.length) : '—';

  return (
    <>
      <div className="kpis">
        <Kpi label="Pedidos totales" value={orders.length} />
        <Kpi label="En curso" value={active.length} />
        <Kpi label="Ventas entregadas" value={money(sales)} />
        <Kpi label="Lead time promedio" value={avgLead} hint="desde que se crea hasta que se entrega" />
      </div>
      <section className="card">
        <h3>Pedidos por estado</h3>
        <div className="bars">
          {STATUS_KEYS.map((s) => {
            const n = orders.filter((o) => o.status === s).length;
            const pct = orders.length ? Math.round((n / orders.length) * 100) : 0;
            return (
              <div className="bar-row" key={s}>
                <span className="bar-label">{STATUS[s].label}</span>
                <div className="bar-track"><div className={`bar-fill badge-${STATUS[s].color}`} style={{ width: `${pct}%` }} /></div>
                <span className="bar-value">{n}</span>
              </div>
            );
          })}
        </div>
      </section>
    </>
  );
}

function OperatorView({ orders, reload, goToOrders }) {
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState('');
  const pending = orders.filter((o) => o.status === 'CREADO');
  const inProgress = orders.filter((o) => ['ACEPTADO', 'EN_PREPARACION', 'DESPACHADO'].includes(o.status));

  async function accept(id) {
    setBusyId(id);
    setError('');
    try {
      await changeStatus(id, 'ACEPTADO');
      await reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <div className="kpis">
        <Kpi label="Por aceptar" value={pending.length} />
        <Kpi label="En preparación" value={orders.filter((o) => o.status === 'EN_PREPARACION').length} />
        <Kpi label="En camino" value={orders.filter((o) => o.status === 'DESPACHADO').length} />
      </div>
      {error && <p className="error">{error}</p>}
      <section className="card">
        <h3>Pedidos por aceptar</h3>
        {pending.length === 0 ? <p className="muted">No hay pedidos nuevos. 🎉</p> : (
          <table>
            <tbody>
              {pending.map((o) => (
                <tr key={o.id}>
                  <td>#{o.id}</td>
                  <td>{o.customerName}</td>
                  <td>{storeName(o.storeId)}</td>
                  <td>{money(o.total)}</td>
                  <td className="right">
                    <button className="btn small" onClick={() => goToOrders(o.id)}>Ver</button>
                    <button className="btn small primary" disabled={busyId === o.id} onClick={() => accept(o.id)}>Aceptar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <section className="card">
        <h3>En curso</h3>
        {inProgress.length === 0 ? <p className="muted">Nada en curso.</p> : (
          <table>
            <tbody>
              {inProgress.map((o) => (
                <tr key={o.id} className="clickable" onClick={() => goToOrders(o.id)}>
                  <td>#{o.id}</td>
                  <td>{o.customerName}</td>
                  <td>{storeName(o.storeId)}</td>
                  <td><StatusBadge status={o.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}

function CustomerView({ orders, goToOrders }) {
  const active = orders.filter((o) => ACTIVE_STATUSES.includes(o.status));
  return (
    <>
      <div className="hero card">
        <div>
          <h3>¿Qué se te antoja hoy?</h3>
          <p className="muted">Haz tu pedido a cualquiera de los locales de la red y síguelo en tiempo real.</p>
        </div>
        <button className="btn primary" onClick={() => goToOrders('new')}>+ Nuevo pedido</button>
      </div>
      <section className="card">
        <h3>Mis pedidos en curso</h3>
        {active.length === 0 ? <p className="muted">No tienes pedidos en curso.</p> : (
          <table>
            <tbody>
              {active.map((o) => (
                <tr key={o.id} className="clickable" onClick={() => goToOrders(o.id)}>
                  <td>#{o.id}</td>
                  <td>{storeName(o.storeId)}</td>
                  <td>{date(o.createdAt)}</td>
                  <td>{money(o.total)}</td>
                  <td><StatusBadge status={o.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}

export default function Dashboard({ session, perms, onlyMine, goToOrders }) {
  const { orders, loading, error, reload } = useOrders({}, { onlyMine, email: session.username });
  const isAdmin = session.roles.includes('Admin');
  const isOperator = session.roles.includes('Operador');

  return (
    <div>
      {loading && <p className="muted">Cargando…</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && (
        <>
          {isAdmin && <AdminView orders={orders} />}
          {!isAdmin && isOperator && perms.cambiarEstado && <OperatorView orders={orders} reload={reload} goToOrders={goToOrders} />}
          {!isAdmin && !isOperator && <CustomerView orders={orders} goToOrders={goToOrders} />}
        </>
      )}
    </div>
  );
}
