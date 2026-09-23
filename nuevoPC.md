# Checklist — Levantar Pedidos360 desde otra PC

Guía rápida para cuando estás en **otro computador** y quieres probar el backend desplegado en AWS. No hay que re-crear nada: los recursos persisten en el lab, solo hay que **encender la instancia** y adaptar 2 cosas al nuevo PC.

## Datos de los recursos (guardar)

| Recurso | Valor |
|---|---|
| Instancia | `ec2-apps` (`i-005db33378ebcb8ea`) |
| Elastic IP (no cambia) | `52.88.99.225` |
| BFF directo | `http://52.88.99.225:8080` |
| API Gateway | `https://byrwcfxrzc.execute-api.us-west-2.amazonaws.com` |
| Clave SSH | `pedidos360.pem` |
| Security Group | `ec2-apps` — puerto 22 (solo IP autorizada) y 8080 (público) |

## Paso 1 — Traer la clave `pedidos360.pem`

Está en la otra PC en `cloudNew\pedidos360.pem`. Pásalo al nuevo PC (pendrive, email, etc.).

- **Windows:** clic derecho → Propiedades → Seguridad → Editar → deja SOLO tu usuario, quita "Usuarios". Si no se arregla, borra y vuelve a copiar el archivo.
- **Mac / Linux:** `chmod 400 pedidos360.pem`

## Paso 2 — Encender el lab y la instancia

1. `awsacademy.com` → curso → **Learner Lab** → **Start Lab**.
2. Consola → **EC2 → Instancias →** `ec2-apps` → **Iniciar instancia** (el lab la deja en `stopped`).
3. Espera ~1 min. Los contenedores Docker (oracle, orders, bff) **arrancan solos** (`restart: unless-stopped`).

## Paso 3 — Autorizar el SSH desde tu nueva IP

El Security Group solo acepta SSH desde la IP de la otra PC.

1. En **¿Cuál es mi IP?** (`whatismyip.com`) copia tu IP actual.
2. Consola → **EC2 → Security Groups →** `ec2-apps` → **Editar reglas de entrada**.
3. Regla `SSH` → **Origen: Mi IP** (o escribe la IP nueva en el campo Custom).
4. Guardar.

> No tienes que tocar la regla del 8080 (ya es pública para el Gateway).

## Paso 4 — Probar por SSH (opcional, si quieres ver los contenedores)

```bash
ssh -i "pedidos360.pem" ec2-user@52.88.99.225
```

En la instancia:

```bash
sudo docker compose -f ~/pedidos360-backend/infra/apps/compose.yml ps
curl -i http://localhost:8080/api/orders   # esperado: 401
```

## Paso 5 — Confirmar desde el navegador

- `http://52.88.99.225:8080/api/orders` → **401** (rechaza sin token, es correcto).
- El API Gateway → `https://byrwcfxrzc.execute-api.us-west-2.amazonaws.com/api/orders` → **401**.

## Paso 6 — El frontend en el nuevo PC

El frontend se ejecuta en local (`npm run dev`). Falta el archivo de configuración (no está en git a propósito):

1. En `pedidos360-frontend/` crea `.env.local`:

```env
VITE_ENTRA_TENANT_ID=9d7e1df5-4b2b-4f57-944c-0ea679424efd
VITE_SPA_CLIENT_ID=e98727c4-f8f2-4762-bf8f-701c36eed7fb
VITE_API_CLIENT_ID=88e22461-0dce-4446-8509-c6eec7da2c02

# Opción A: probar contra el BFF directo (magenta: debug de authorities)
VITE_API_BASE_URL=http://52.88.99.225:8080

# Opción B: arquitectura final con API Gateway
# VITE_API_BASE_URL=https://byrwcfxrzc.execute-api.us-west-2.amazonaws.com
```

2. Reinicia `npm run dev` (los `.env` se leen al arrancar).
3. Inicia sesión con MSAL y prueba el botón del token (debería responder 200).

## Solución de problemas

| Síntoma | Causa y arreglo |
|---|---|
| Front "no se pudo conectar con el bff" | `.env.local` apunta a `localhost:8080`; cambia a la URL de la opción A o B. |
| `ssh: Connection refused / timed out` | La regla 22 del SG aún tiene la IP vieja → Paso 3. |
| 401 con token desde el Gateway | Revisa issuer/audience del autorizador (`GUIA-AWS.md` paso 3.2). |
| 200 OK pero CORS bloqueado | Vite corriendo en puerto distinto a `5173`; el BFF solo acepta `http://localhost:5173`. |
| "No existe la instancia" | El lab venció (24 hs sin usarse); vuelve a lanzarla según `docs/GUIA-AWS.md` paso 1. Última opción: volver a crearla. |

## Recordatorios

- ⚠️ Las credenciales de AWS CLI **vencen** al cerrar el lab; configúralas de nuevo con `aws configure` (no las compartas por chat).
- ⚠️ `pedidos360.pem` es una clave privada: compártela solo por canales que controlas y **no la copies dentro del repo de git** (no está en `.gitignore`).
- Al terminar el lab, la instancia se apaga. Se conservan: Elastic IP, volúmenes (Oracle), Security Group y API Gateway.