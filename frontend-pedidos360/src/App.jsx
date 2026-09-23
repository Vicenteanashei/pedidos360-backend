import { useRef, useState } from 'react';
import { useSession } from './auth/AuthGate';
import LoginSection from './auth/LoginPage';
import { BackendTestSection, TokenSection } from './components/SessionInfo';
import Dashboard from './components/Dashboard';
import OrdersPage from './components/OrdersPage';
import { can, ROLE_DESCRIPTIONS } from './auth/roles';

export default function App() {
  const session = useSession();
  const ordersRef = useRef(null);
  // Que abrir en la seccion de pedidos: un id, 'new' o nada
  const [openTarget, setOpenTarget] = useState(null);

  const perms = {
    verPedidos: can(session.roles, 'verPedidos'),
    crearPedido: can(session.roles, 'crearPedido'),
    editarPedido: can(session.roles, 'editarPedido'),
    cambiarEstado: can(session.roles, 'cambiarEstado'),
    eliminarPedido: can(session.roles, 'eliminarPedido'),
  };
  // El cliente solo ve sus propios pedidos (los asociados a su correo)
  const onlyMine = session.roles.length === 1 && session.roles[0] === 'Cliente';
  const ready = session.account && session.rolesLoaded && session.roles.length > 0;

  function goToOrders(target = null) {
    setOpenTarget(target);
    setTimeout(() => ordersRef.current?.scrollIntoView({ behavior: 'smooth' }), 0);
  }

  const ctx = { session, perms, onlyMine, goToOrders };

  return (
    <div className="page">
      <h1>Pedidos360</h1>
      <p className="sub">Inicia sesión con Microsoft Entra ID, revisa el token y gestiona los pedidos según tu rol.</p>

      <LoginSection session={session} />
      <TokenSection session={session} />
      <BackendTestSection session={session} />

      <section className="section">
        <h2>4. Resumen según tu rol</h2>
        {!session.account && <p className="warn">Primero inicia sesión.</p>}
        {session.account && !session.rolesLoaded && <p className="muted">Cargando…</p>}
        {session.account && session.rolesLoaded && session.roles.length === 0 && (
          <p className="warn">⚠ Tu usuario no tiene rol asignado. Pide que te asignen Admin, Operador o Cliente y vuelve a iniciar sesión.</p>
        )}
        {ready && (
          <>
            <p className="muted small">
              {session.roles.map((r) => <span key={r}><b>{r}:</b> {ROLE_DESCRIPTIONS[r]} </span>)}
            </p>
            <Dashboard {...ctx} />
          </>
        )}
      </section>

      <section className="section" ref={ordersRef}>
        <h2>5. {onlyMine ? 'Mis pedidos' : 'Pedidos'}</h2>
        {!ready ? <p className="warn">Disponible cuando inicies sesión con un usuario que tenga rol.</p>
          : <OrdersPage key={String(openTarget)} {...ctx} openTarget={openTarget} />}
      </section>
    </div>
  );
}
