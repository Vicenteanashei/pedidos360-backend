// Seccion 1: estado de la sesion y botones de login/logout
export default function LoginSection({ session }) {
  const { account, username, roles, rolesLoaded, busy, error, login, logout } = session;
  return (
    <section className="section">
      <h2>1. Iniciar sesión</h2>
      <div className="who">
        {account ? <>Sesión iniciada como <b>{username}</b></> : 'Sin sesión.'}
        {account && rolesLoaded && (
          roles.length > 0
            ? roles.map((r) => <span key={r} className={`role role-${r.toLowerCase()}`}>{r}</span>)
            : <span className="role role-none">sin rol</span>
        )}
      </div>
      <button className="btn primary" onClick={login} disabled={busy}>
        {busy ? 'Conectando…' : 'Iniciar sesión con Microsoft'}
      </button>
      <button className="btn" onClick={logout} disabled={!account}>Cerrar sesión</button>
      {error && <p className="error">Error: {error}</p>}
    </section>
  );
}
