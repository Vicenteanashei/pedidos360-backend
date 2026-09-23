import { useState } from 'react';
import Dashboard from './components/Dashboard';
import OrdersPage from './components/OrdersPage';
import { can, ROLE_DESCRIPTIONS } from './auth/roles';

export default function App({ session }) {
  const [view, setView] = useState('inicio');
  // Que abrir al entrar a Pedidos: un id, 'new' o nada
  const [openTarget, setOpenTarget] = useState(null);

  // Permisos del usuario segun sus roles (un usuario puede tener mas de uno)
  const perms = {
    verPedidos: can(session.roles, 'verPedidos'),
    crearPedido: can(session.roles, 'crearPedido'),
    editarPedido: can(session.roles, 'editarPedido'),
    cambiarEstado: can(session.roles, 'cambiarEstado'),
    eliminarPedido: can(session.roles, 'eliminarPedido'),
  };
  // El cliente solo ve sus propios pedidos (los asociados a su correo)
  const onlyMine = session.roles.length === 1 && session.roles[0] === 'Cliente';

  function goToOrders(target = null) {
    setOpenTarget(target);
    setView('pedidos');
  }

  const ctx = { session, perms, onlyMine, goToOrders };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">🥐 Pedidos360</div>
        <nav>
          <button className={view === 'inicio' ? 'tab active' : 'tab'} onClick={() => setView('inicio')}>Inicio</button>
          <button className={view === 'pedidos' ? 'tab active' : 'tab'} onClick={() => goToOrders()}>
            {onlyMine ? 'Mis pedidos' : 'Pedidos'}
          </button>
        </nav>
        <div className="user">
          <div>
            <div className="user-name">{session.name}</div>
            <div className="roles">
              {session.roles.map((r) => (
                <span key={r} className={`role role-${r.toLowerCase()}`} title={ROLE_DESCRIPTIONS[r]}>{r}</span>
              ))}
            </div>
          </div>
          <button className="btn ghost" onClick={session.logout}>Cerrar sesión</button>
        </div>
      </header>

      <main className="content">
        {view === 'inicio' && <Dashboard {...ctx} />}
        {view === 'pedidos' && <OrdersPage key={String(openTarget)} {...ctx} openTarget={openTarget} />}
      </main>
    </div>
  );
}
