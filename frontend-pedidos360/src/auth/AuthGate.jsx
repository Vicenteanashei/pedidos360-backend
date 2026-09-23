import { useEffect, useState } from 'react';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { tokenRequest } from './authConfig';
import { decodeClaims, getAccessToken } from './token';
import { ROLES } from './roles';
import { setTokenProvider } from '../api/http';

const message = (e) => (e instanceof Error ? e.message : String(e));

// Estado de la sesion con Entra ID: cuenta, roles del access token, login/logout y token para las llamadas
export function useSession() {
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

  return {
    account,
    name: account ? account.name || account.username : '',
    username: account?.username ?? '',
    roles: roles ?? [],
    rolesLoaded: roles !== null,
    busy: busy || inProgress !== InteractionStatus.None,
    error,
    login,
    logout: () => instance.logoutPopup({ account }),
    getToken: () => getAccessToken(instance, account),
  };
}
