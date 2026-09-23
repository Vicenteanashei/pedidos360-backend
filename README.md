# Pedidos360

Este es **Pedidos360**, una plataforma pensada para una red de 20 PyMEs (panaderías y cafés) que necesitan recibir pedidos por la web, coordinar la cocina y los despachos, y saber en todo momento en qué estado está cada pedido.

Lo desarrollamos para la Evaluación Final Transversal de **Desarrollo Cloud Native I (DSY1107)**. Está hecho con Spring Boot 3.5 y Java 21, separado en microservicios que corren en Docker.

## Qué hay en este repo

- **`frontend-pedidos360`**: la aplicación web, hecha en React. El usuario inicia sesión con su cuenta de Microsoft y ve opciones distintas según su rol.
- **`ms-pedidos360-orders`**: el corazón del sistema. Aquí se crean, consultan, editan y eliminan los pedidos, y se controla que cada cambio de estado tenga sentido.
- **`ms-pedidos360-bff`**: la puerta de entrada. Es el único servicio que recibe llamadas desde afuera. Revisa que quien llama haya iniciado sesión con Microsoft y que su rol le permita hacer lo que está pidiendo. Si todo está en orden, le pasa la solicitud al microservicio que corresponde.
- **`infra/apps/compose.yml`**: levanta todo el backend con un solo comando (la base de datos Oracle, orders y el BFF).
- **`docs/GUIA-AWS.md`**: la guía paso a paso para desplegar en AWS (EC2 + API Gateway).

Todavía faltan el catálogo de productos, las notificaciones con RabbitMQ y la auditoría y reportería con Kafka. Los vamos a ir agregando en este mismo repo.

## Cómo funciona

Así viaja un pedido desde que el usuario hace clic hasta que llega a la base de datos:

```
Front React (MSAL)  ── token ──▶  API Gateway  ──▶  ms-pedidos360-bff :8080
                                                        │ revisa el token y el rol
                                                        ▼ (red interna de Docker)
                                         ms-pedidos360-orders :8081  ──▶  Oracle
```

1. El usuario inicia sesión con su cuenta de Microsoft (Entra ID) y el frontend recibe un token.
2. Cada llamada al backend va con ese token.
3. En AWS, el API Gateway hace un primer filtro y rechaza los tokens inválidos. En local nos saltamos este paso y el front le habla directo al BFF.
4. El BFF vuelve a validar el token y revisa el rol del usuario.
5. Orders hace el trabajo y guarda en Oracle.

Orders y la base de datos no quedan expuestos hacia afuera, porque solo el BFF puede hablar con ellos.

## Roles y permisos

Usamos tres roles, que se asignan a cada usuario en Azure:

| Acción | Cliente | Operador | Admin |
|---|:---:|:---:|:---:|
| Ver pedidos | ✔ | ✔ | ✔ |
| Crear un pedido | ✔ | ✔ | |
| Editar un pedido (solo si sigue en `CREADO`) | ✔ | ✔ | |
| Cambiar el estado de un pedido | | ✔ | ✔ |
| Eliminar un pedido | | | ✔ |

Si alguien intenta algo que su rol no permite, recibe un **403**. Si ni siquiera inició sesión, recibe un **401**.

## El ciclo de vida de un pedido

```
CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO
```

Un pedido se puede **cancelar** en cualquier momento antes de ser entregado. La regla más importante del caso es que **no se puede despachar un pedido que no fue aceptado**. Si alguien lo intenta, el sistema responde con un **409** y explica qué estados son válidos desde el actual.

Además guardamos la fecha en que el pedido se aceptó, se despachó y se entregó. Con eso después vamos a calcular el *lead time* (cuánto se demora un pedido desde que se crea hasta que se entrega).

## Levantarlo en tu computador

### Con Docker (la forma recomendada)

Solo necesitas Docker instalado. Desde la raíz del repo:

```bash
docker compose -f infra/apps/compose.yml up -d --build
```

La primera vez se demora un poco: hay que descargar Oracle y compilar los servicios. Oracle tarda cerca de un minuto en quedar lista, y orders espera a que esté disponible antes de arrancar.

No hace falta configurar nada más, porque el compose ya trae los datos de Azure y claves de desarrollo. Si quieres cambiar algo (por ejemplo, apuntar a la base de datos en la nube o poner contraseñas reales en el servidor), copia `infra/apps/.env.example` como `infra/apps/.env` y edítalo. Ese archivo nunca se sube a GitHub.

Para comprobar que está funcionando:

```bash
# Sin token: debería responder 401
curl -i http://localhost:8080/api/orders

# Con un token real de Microsoft: 200 (o 403 si tu rol no alcanza)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders
```

### Sin Docker (para programar más rápido)

Orders también puede correr solo, con una base de datos en memoria (H2), así no tienes que levantar Oracle:

```bash
cd ms-pedidos360-orders
sh mvnw spring-boot:run
```

Queda en `http://localhost:8081` y puedes probar todos los endpoints desde Swagger en `http://localhost:8081/swagger-ui.html`.

## El frontend

Cada rol ve una aplicación distinta:

- **Admin**: un panel con indicadores (pedidos totales, en curso, ventas entregadas y lead time promedio) y el detalle por estado. Puede mover pedidos por el flujo y eliminarlos.
- **Operador**: ve los pedidos nuevos y los acepta con un clic, sigue los que están en curso, crea y edita pedidos y los avanza hasta la entrega.
- **Cliente**: ve solo sus pedidos, hace pedidos nuevos eligiendo local y productos, los edita mientras no hayan sido aceptados y sigue su estado.

Si un usuario entra sin rol asignado, la app se lo avisa y le explica qué pedir. Los botones se muestran según el rol, pero quien realmente decide es el BFF: aunque alguien fuerce una llamada, el backend responde 403.

Mientras no exista el microservicio de catálogo, los locales y productos vienen de una lista provisional en `src/constants/catalog.js`.

Para levantarlo necesitas Node.js 20 o superior, y el backend corriendo:

```bash
cd frontend-pedidos360
npm install
npm run dev
```

Queda en `http://localhost:5173` y le habla al BFF en `http://localhost:8080`. Los IDs de Azure ya vienen configurados; si quieres apuntar a otro backend (por ejemplo, el API Gateway en AWS), crea `frontend-pedidos360/.env.local` con `VITE_API_BASE_URL=<url>` (hay un ejemplo en `.env.example`).

En Azure, el registro de la SPA tiene que tener como URI de redirección `http://localhost:5173/redirect.html` (plataforma "Aplicación de página única").

## Endpoints de pedidos

| Método | Ruta | Para qué sirve |
|---|---|---|
| POST | `/api/orders` | Crear un pedido nuevo (parte en `CREADO`) |
| GET | `/api/orders/{id}` | Ver un pedido |
| GET | `/api/orders?status=&from=&to=` | Listar pedidos, con filtro opcional por estado y fechas |
| PUT | `/api/orders/{id}` | Editar un pedido que todavía no ha sido aceptado |
| PUT | `/api/orders/{id}/status` | Cambiar el estado, por ejemplo `{ "status": "ACEPTADO" }` |
| DELETE | `/api/orders/{id}` | Eliminar un pedido |

Las fechas de los filtros van en formato ISO, por ejemplo `2026-09-10T00:00:00`.

## Configuración en Azure (Entra ID)

El login lo maneja Microsoft Entra ID, en el tenant `vicho1.onmicrosoft.com`. Para que el backend acepte los tokens, el registro de la API en Azure tiene que cumplir con esto:

- En el manifest, `requestedAccessTokenVersion` tiene que estar en `2`. Si queda en 1, el token llega con otro emisor y todas las llamadas fallan con 401.
- Tiene que exponer el permiso `access_as_user`.
- Tiene que tener creados los roles `Admin`, `Operador` y `Cliente`, escritos exactamente así.
- Cada usuario necesita un rol asignado. Eso se hace en **Aplicaciones empresariales → (la API) → Usuarios y grupos**.

Estos son los valores que usa el BFF para validar el token:

| | |
|---|---|
| Emisor (issuer) | `https://login.microsoftonline.com/<ENTRA_TENANT_ID>/v2.0` |
| Audiencia | el Client ID del registro de la API |
| Scope | `api://<API_CLIENT_ID>/access_as_user` |

## Tests

Cada microservicio tiene sus propias pruebas. Las de orders recorren el ciclo completo de un pedido y comprueban la regla de "no despachar sin aceptar". Las del BFF comprueban que se responda 401 sin sesión y 403 cuando el rol no corresponde.

```bash
cd ms-pedidos360-orders && sh mvnw test
cd ms-pedidos360-bff && sh mvnw test
```

En Windows se usa `mvnw.cmd test`. Si no tienes Maven instalado no pasa nada: `mvnw` lo descarga la primera vez.
