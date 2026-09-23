import { STATUS } from '../constants/orderStatus';

export default function StatusBadge({ status }) {
  const s = STATUS[status] ?? { label: status, color: 'gray' };
  return <span className={`badge badge-${s.color}`}>{s.label}</span>;
}
