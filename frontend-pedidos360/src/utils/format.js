const clp = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('es-CL', { dateStyle: 'short', timeStyle: 'short' });

export const money = (n) => clp.format(Number(n ?? 0));
export const date = (iso) => (iso ? dateTime.format(new Date(iso)) : '—');

// Milisegundos en texto: "25 min" o "1 h 25 min"
export function formatDuration(ms) {
  if (ms == null || Number.isNaN(ms)) return '—';
  const min = Math.round(ms / 60000);
  return min < 60 ? `${min} min` : `${Math.floor(min / 60)} h ${min % 60} min`;
}

export const duration = (fromIso, toIso) =>
  fromIso && toIso ? formatDuration(new Date(toIso) - new Date(fromIso)) : '—';
