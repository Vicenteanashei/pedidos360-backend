# Pedidos360 — Backend

Backend cloud-native (Spring Boot 3.5, Java 21) para la gestión de pedidos y despacho de una red de PyMEs (panaderías/cafés).

## Estructura

```
ms-pedidos360-orders/    Pedidos: CRUD + flujo de estados (Oracle)
ms-pedidos360-bff/       Valida el JWT de Entra ID, autoriza por rol y reenvía a los micros
infra/apps/compose.yml   Docker Compose de la VM de aplicaciones (Oracle + orders + bff)
```

Próximos: `ms-pedidos360-catalog`, `ms-pedidos360-notify` (RabbitMQ), `ms-pedidos360-audit` y `ms-pedidos360-report` (Kafka), `infra/mq`, `infra/kafka`.

## Arquitectura

```
Front React (MSAL)   ── Bearer ──▶ [API Gateway: JWT Authorizer] ──▶ ms-pedidos360-bff :8080
                                                                        │ valida JWT + rol
                                                                        ▼ red interna Docker
                                                          ms-pedidos360-orders :8081 ──▶ Oracle
```

Entra ID solo emite el token. En local el front llama directo al BFF (`http://localhost:8080`); en AWS se llama al API Gateway.

## Entra ID

| Dato | Valor |
|---|---|
| Tenant | `vicho1.onmicrosoft.com` |
| Issuer | `https://login.microsoftonline.com/<ENTRA_TENANT_ID>/v2.0` |
| Audience | `<API_CLIENT_ID>` (registro de la API) |
| Scope | `api://<API_CLIENT_ID>/access_as_user` |
| App roles | `Admin`, `Operador`, `Cliente` (valor exacto) |

Requisitos en el registro de la API: manifest con `requestedAccessTokenVersion: 2`, scope `access_as_user`, y los app roles asignados a usuarios en **Aplicaciones empresariales → (API) → Usuarios y grupos**.

## Levantar con Docker

```bash
docker compose -f infra/apps/compose.yml up -d --build
```

No necesita `.env`: el compose trae por defecto los IDs de Entra ID y credenciales de desarrollo. Para cambiar algo (por ejemplo `DB_URL` de la BD cloud o contraseñas reales en la EC2), copiar `infra/apps/.env.example` como `infra/apps/.env` y editarlo; ese archivo no se sube a git.

La primera vez Oracle tarda ~1 min en quedar `healthy`; `orders` espera a que esté lista.

Prueba rápida:

```bash
curl -i http://localhost:8080/api/orders                                   # 401
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders # 200 / 403 según rol
```

## Levantar sin Docker (H2 en memoria)

```bash
cd ms-pedidos360-orders && sh mvnw spring-boot:run  # :8081, Swagger en /swagger-ui.html
```

## Endpoints — ms-pedidos360-orders

| Método | Ruta | Roles (BFF) | Descripción |
|---|---|---|---|
| POST | `/api/orders` | Operador, Cliente | Crear pedido (estado `CREADO`) |
| GET | `/api/orders/{id}` | todos | Obtener pedido |
| GET | `/api/orders?status=&from=&to=` | todos | Listar con filtros (fechas ISO `2026-09-10T00:00:00`) |
| PUT | `/api/orders/{id}` | Operador, Cliente | Editar (solo en `CREADO`) |
| PUT | `/api/orders/{id}/status` | Admin, Operador | Cambiar estado `{ "status": "ACEPTADO" }` |
| DELETE | `/api/orders/{id}` | Admin | Eliminar |

Flujo: `CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO` (se puede `CANCELADO` antes de entregar). No se puede despachar sin aceptar (409).

## Tests

`mvnw` usa el `mvn` instalado; si no hay, descarga Maven 3.9.9 una sola vez en `~/.m2/wrapper`. En Windows: `mvnw.cmd test`.

```bash
cd ms-pedidos360-orders && sh mvnw test
cd ms-pedidos360-bff && sh mvnw test
```
