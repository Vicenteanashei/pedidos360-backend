import { useEffect, useState } from 'react';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { tokenRequest } from './authConfig';
import { decodeClaims, getAccessToken } from './token';
import { ROLES } from './roles';
import { setTokenProvider } from '../api/http';
import LoginPage from './LoginPage';

const message = (e) => (e instanceof Error ? e.message : String(e));

// Sin cuenta muestra el login. Con cuenta obtiene el access token, lee los roles
// y registra el token para que cada llamada al backend lo lleve.
export default function AuthGate({ children }) {
  const { instance, accounts, inProgress } = useMsal();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [roles, setRoles] = useState(null);
  const account = accounts[0];

  useEffect(() => {
    if (!account) {
      setRoles(null);
      return undefined;
    }
    let cancelled = false;
    setTokenProvider(() => getAccessToken(instance, account));
    getAccessToken(instance, account)
      .then((token) => {
        if (!cancelled) setRoles((decodeClaims(token).roles ?? []).filter((r) => ROLES.includes(r)));
      })
      .catch((e) => !cancelled && setError(message(e)));
    return () => {
      cancelled = true;
      setTokenProvider(async () => null);
    };
  }, [instance, account]);

  async function login() {
    setBusy(true);
    setError('');
    try {
      await instance.loginPopup({ ...tokenRequest, prompt: 'select_account' });
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }

  const logout = () => instance.logoutPopup({ account });

  if (!account) {
    return <LoginPage onLogin={login} busy={busy || inProgress !== InteractionStatus.None} error={error} />;
  }
  if (roles === null) {
    return error ? <LoginPage onLogin={login} busy={false} error={error} /> : <div className="loading">Cargando sesión…</div>;
  }
  if (roles.length === 0) {
    return (
      <div className="login-screen">
        <div className="login-card">
          <div className="brand-big">🥐 Pedidos360</div>
          <h3>Tu usuario no tiene un rol asignado</h3>
          <p className="muted">
            Iniciaste sesión como <b>{account.username}</b>, pero para usar la plataforma necesitas el rol
            Admin, Operador o Cliente. Pídele al administrador que te lo asigne en Entra ID (Aplicaciones
            empresariales → API → Usuarios y grupos) y vuelve a entrar.
          </p>
          <button className="btn" onClick={logout}>Cerrar sesión</button>
        </div>
      </div>
    );
  }

  return children({ name: account.name || account.username, username: account.username, roles, logout });
}
