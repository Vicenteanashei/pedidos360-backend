// Identidades de Entra ID (no son secretos). Se pueden sobreescribir con .env.local.
const tenantId = import.meta.env.VITE_ENTRA_TENANT_ID || '9d7e1df5-4b2b-4f57-944c-0ea679424efd';
const spaClientId = import.meta.env.VITE_SPA_CLIENT_ID || 'e98727c4-f8f2-4762-bf8f-701c36eed7fb';
const apiClientId = import.meta.env.VITE_API_CLIENT_ID || '88e22461-0dce-4446-8509-c6eec7da2c02';

export const msalConfig = {
  auth: {
    clientId: spaClientId,
    authority: `https://login.microsoftonline.com/${tenantId}`,
    redirectUri: `${window.location.origin}/redirect.html`,
    postLogoutRedirectUri: window.location.origin,
  },
  cache: {
    cacheLocation: 'sessionStorage',
  },
};

// Permiso delegado que expone el registro de la API
export const tokenRequest = {
  scopes: [`api://${apiClientId}/access_as_user`],
};
