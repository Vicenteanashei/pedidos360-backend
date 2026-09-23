import { createRoot } from 'react-dom/client';
import { PublicClientApplication } from '@azure/msal-browser';
import { MsalProvider } from '@azure/msal-react';
import { msalConfig } from './auth/authConfig';
import App from './App';
import './styles.css';

// MsalProvider se encarga de inicializar MSAL
const msal = new PublicClientApplication(msalConfig);

createRoot(document.getElementById('root')).render(
  <MsalProvider instance={msal}>
    <App />
  </MsalProvider>,
);
