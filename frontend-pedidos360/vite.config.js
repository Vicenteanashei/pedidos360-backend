import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

// Puerto fijo: tiene que coincidir con las Redirect URI del registro de la SPA en Entra ID
// y con CORS_ALLOWED_ORIGINS del BFF.
// Multipágina: redirect.html (el puente del popup de MSAL) también tiene que quedar en el build.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  preview: { port: 5173, strictPort: true },
  build: {
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        redirect: fileURLToPath(new URL('./redirect.html', import.meta.url)),
      },
    },
  },
});
