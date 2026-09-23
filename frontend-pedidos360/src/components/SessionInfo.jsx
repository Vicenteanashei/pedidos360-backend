import { useEffect, useState } from 'react';
import { decodeClaims } from '../auth/token';
import { API_URL } from '../api/http';

// Mismos valores que validan el BFF y el API Gateway
const TENANT_ID = import.meta.env.VITE_ENTRA_TENANT_ID || '9d7e1df5-4b2b-4f57-944c-0ea679424efd';
const API_CLIENT_ID = import.meta.env.VITE_API_CLIENT_ID || '88e22461-0dce-4446-8509-c6eec7da2c02';
const EXPECTED_ISS = `https://login.microsoftonline.com/${TENANT_ID}/v2.0`;

function Check({ ok, level = 'bad', children }) {
  const cls = ok ? 'ok' : level;
  const icon = ok ? '✔️' : level === 'warn' ? '⚠' : '✘';
  return <li className={cls}>{icon} {children}</li>;
}

// Seccion 2: ¿el token viene bien?
export function TokenSection({ session }) {
  const [token, setToken] = useState(null);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!session.account) {
      setToken(null);
      return;
    }
    session.getToken().then(setToken).catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.account]);

  let body;
  if (!session.account) {
    body = <ul className="checks"><li className="warn">Primero inicia sesión.</li></ul>;
  } else if (error) {
    body = <p className="error">{error}</p>;
  } else if (!token) {
    body = <p className="muted">Obteniendo token…</p>;
  } else {
    const c = decodeClaims(token);
    const scopes = (c.scp || '').split(' ');
    const roles = c.roles || [];
    const rows = {
      usuario: c.preferred_username || c.upn || session.username,
      nombre: c.name,
      iss: c.iss,
      aud: c.aud,
      scp: c.scp,
      roles: roles.join(', ') || '(ninguno)',
      expira: new Date(c.exp * 1000).toLocaleString(),
    };
    body = (
      <>
        <ul className="checks">
          <Check ok={c.iss === EXPECTED_ISS}>
            {c.iss === EXPECTED_ISS ? 'Emisor correcto (token v2).'
              : `Emisor inesperado: ${c.iss}. Si empieza con sts.windows.net, pon requestedAccessTokenVersion: 2 en el manifest de la API.`}
          </Check>
          <Check ok={c.aud === API_CLIENT_ID || c.aud === `api://${API_CLIENT_ID}`}>
            {c.aud === API_CLIENT_ID || c.aud === `api://${API_CLIENT_ID}` ? `Audiencia = la API (${c.aud}).` : `Audiencia inesperada: ${c.aud}.`}
          </Check>
          <Check ok={scopes.includes('access_as_user')}>
            {scopes.includes('access_as_user') ? 'Trae el scope access_as_user.' : 'No trae el scope access_as_user.'}
          </Check>
          <Check ok={roles.length > 0} level="warn">
            {roles.length > 0 ? `Roles: ${roles.join(', ')}`
              : 'El usuario no tiene rol asignado: el login funciona, pero el BFF va a responder 403. Asígnale Admin/Operador/Cliente en Aplicaciones empresariales → (API) → Usuarios y grupos, y vuelve a iniciar sesión.'}
          </Check>
        </ul>
        <table className="claims">
          <tbody>
            {Object.entries(rows).map(([k, v]) => <tr key={k}><td>{k}</td><td>{v ?? ''}</td></tr>)}
          </tbody>
        </table>
      </>
    );
  }

  async function copy() {
    await navigator.clipboard.writeText(token);
    setCopied(true);
  }

  return (
    <section className="section">
      <h2>2. ¿El token viene bien?</h2>
      {body}
      <button className="btn" onClick={copy} disabled={!token}>
        {copied ? '¡Copiado! (no lo compartas)' : 'Copiar access token (para curl)'}
      </button>
    </section>
  );
}

// Seccion 3: probar el token contra el backend (BFF o API Gateway)
export function BackendTestSection({ session }) {
  const [result, setResult] = useState('—');

  async function call(withToken) {
    const url = `${API_URL}/api/orders`;
    setResult(`Llamando a ${url} …`);
    try {
      const headers = withToken ? { Authorization: `Bearer ${await session.getToken()}` } : {};
      const res = await fetch(url, { headers });
      const text = await res.text();
      const expected = withToken ? 200 : 401;
      let note = res.status === expected ? '✔ Resultado esperado.' : '';
      if (withToken && res.status === 401) note = '✘ El BFF rechazó el token: revisa ENTRA_TENANT_ID y API_CLIENT_ID del BFF.';
      if (withToken && res.status === 403) note = '⚠ Token válido pero el rol no alcanza (o el usuario no tiene rol).';
      setResult(`HTTP ${res.status}\n${note}\n\n${text || '(sin cuerpo)'}`);
    } catch (e) {
      setResult(`No se pudo conectar con el BFF (${e.message}).\n¿Está levantado en ${url}? Si lo está, revisa que CORS permita ${location.origin}.`);
    }
  }

  return (
    <section className="section">
      <h2>3. Probar contra el BFF</h2>
      <p className="muted small">URL del backend: <code>{API_URL}</code></p>
      <button className="btn" onClick={() => call(false)}>GET /api/orders sin token (esperado 401)</button>
      <button className="btn" onClick={() => call(true)} disabled={!session.account}>GET /api/orders con token (esperado 200)</button>
      <pre className="result">{result}</pre>
    </section>
  );
}
