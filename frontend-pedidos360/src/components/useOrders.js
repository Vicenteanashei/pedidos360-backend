import { useCallback, useEffect, useState } from 'react';
import { listOrders } from '../api/ordersApi';

// Carga pedidos con filtros; si onlyMine, deja solo los del correo del usuario
export function useOrders(filters, { onlyMine, email }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const reload = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await listOrders(filters);
      const mine = email?.toLowerCase();
      setOrders(onlyMine ? data.filter((o) => o.customerEmail?.toLowerCase() === mine) : data);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters?.status, filters?.from, filters?.to, onlyMine, email]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { orders, loading, error, reload };
}
