import StatusBadge from './StatusBadge';
import { storeName } from '../constants/catalog';
import { date, money } from '../utils/format';

export default function OrderList({ orders, selectedId, onSelect, empty }) {
  if (orders.length === 0) return <p className="muted">{empty}</p>;
  return (
    <table>
      <thead>
        <tr><th>#</th><th>Cliente</th><th>Local</th><th>Creado</th><th>Total</th><th>Estado</th></tr>
      </thead>
      <tbody>
        {orders.map((o) => (
          <tr key={o.id} className={`clickable ${o.id === selectedId ? 'selected' : ''}`} onClick={() => onSelect(o.id)}>
            <td>{o.id}</td>
            <td>{o.customerName}</td>
            <td>{storeName(o.storeId)}</td>
            <td>{date(o.createdAt)}</td>
            <td>{money(o.total)}</td>
            <td><StatusBadge status={o.status} /></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
