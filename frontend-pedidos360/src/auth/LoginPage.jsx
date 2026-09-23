function MicrosoftLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 21 21" aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#f25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
      <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
      <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
    </svg>
  );
}

export default function LoginPage({ onLogin, busy, error }) {
  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="brand-big">🥐 Pedidos360</div>
        <p className="muted">Pedidos y despacho para la red de panaderías y cafés</p>
        <button className="btn ms-btn" onClick={onLogin} disabled={busy}>
          <MicrosoftLogo />
          {busy ? 'Conectando…' : 'Iniciar sesión con Microsoft'}
        </button>
        {error && <p className="error" role="alert">{error}</p>}
        <p className="muted small">Acceso para Admin, Operador y Cliente.</p>
      </div>
    </div>
  );
}
