import { InteractionRequiredAuthError } from '@azure/msal-browser';
import { tokenRequest } from './authConfig';

// Access token de la API desde la cache; si expiro o requiere interaccion, abre un popup
export async function getAccessToken(instance, account) {
  const request = { ...tokenRequest, account };
  try {
    return (await instance.acquireTokenSilent(request)).accessToken;
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      return (await instance.acquireTokenPopup(request)).accessToken;
    }
    throw error;
  }
}

// Lee los claims del JWT (solo para mostrar datos y roles en la UI; la validacion real la hace el backend)
export function decodeClaims(jwt) {
  const payload = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  const padded = payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=');
  const json = decodeURIComponent(
    atob(padded)
      .split('')
      .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
      .join(''),
  );
  return JSON.parse(json);
}
