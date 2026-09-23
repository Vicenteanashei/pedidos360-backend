# Guía de despliegue en AWS — Pedidos360

Esta guía es para la parte de AWS del proyecto. La parte de Azure (login con Microsoft) y el código del backend y el frontend ya están listos y probados en local. Lo que queda es **publicar el backend en una EC2 y ponerle un API Gateway adelante** que valide el token de Azure.

Al terminar, el flujo va a quedar así:

```
Front React (en el PC)  ──token──▶  API Gateway (valida el JWT)  ──▶  EC2: ms-pedidos360-bff :8080
                                                                              │ (red interna Docker)
                                                                              ▼
                                                                  ms-pedidos360-orders ──▶ Oracle
```

> Tiempo estimado: 1 a 2 horas, la primera vez.

---

## Lo que necesitas antes de empezar

- Acceso al repo en GitHub (pídele a Vicente que te agregue como colaborador si es privado).
- Tu cuenta de AWS (AWS Academy / Learner Lab sirve).
- Estos datos de Azure, que ya están configurados en el proyecto:

| Dato | Valor |
|---|---|
| Tenant ID | `9d7e1df5-4b2b-4f57-944c-0ea679424efd` |
| Client ID de la API (audiencia) | `88e22461-0dce-4446-8509-c6eec7da2c02` |
| Issuer | `https://login.microsoftonline.com/9d7e1df5-4b2b-4f57-944c-0ea679424efd/v2.0` |
| Usuario de prueba | `test1@vicho1.onmicrosoft.com` (la clave te la pasa Vicente) |

---

## Paso 1 — Crear la EC2 de aplicaciones (`ec2-apps`)

En la consola de AWS → **EC2 → Lanzar instancia**:

| Campo | Valor |
|---|---|
| Nombre | `ec2-apps` |
| AMI | Amazon Linux 2023 |
| Tipo | **t3.medium** (mínimo). Oracle + 2 microservicios necesitan ~4 GB de RAM; con una t2.micro no arranca. |
| Par de claves | Crea uno (`pedidos360.pem`) y guárdalo bien |
| Almacenamiento | 20 GB |

**Grupo de seguridad** (Security Group) `sg-ec2-apps`, reglas de entrada:

| Puerto | Origen | Para qué |
|---|---|---|
| 22 (SSH) | Mi IP | Entrar a la máquina |
| 8080 (TCP) | 0.0.0.0/0 | Lo llama el API Gateway (sale por IPs públicas de AWS, no hay un rango fijo) |

Oracle y ms-orders **no** necesitan reglas: viven en la red interna de Docker y no se exponen.

**Elastic IP:** EC2 → Direcciones IP elásticas → Asignar → Asociar a `ec2-apps`. Es importante: en AWS Academy la IP pública cambia cada vez que se reinicia el lab, y eso rompería la conexión con el API Gateway.

---

## Paso 2 — Instalar Docker y levantar el backend

Conéctate por SSH (en Linux/Mac primero `chmod 400 pedidos360.pem`):

```bash
ssh -i "pedidos360.pem" ec2-user@<ELASTIC_IP>
```

Instala Docker, Git y los plugins de Compose y Buildx:

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
exit   # sal y vuelve a entrar por SSH para que tome el grupo docker
```

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo curl -SL https://github.com/docker/buildx/releases/download/v0.19.3/buildx-v0.19.3.linux-amd64 \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/*
docker compose version   # debe mostrar la versión
```

Clona el repo y levanta todo:

```bash
git clone https://github.com/<usuario>/pedidos360-backend.git
cd pedidos360-backend
docker compose -f infra/apps/compose.yml up -d --build
```

La primera vez demora varios minutos (descarga Oracle y compila los microservicios). Revisa que todo esté arriba:

```bash
docker compose -f infra/apps/compose.yml ps      # oracle debe quedar "healthy"
curl -i http://localhost:8080/api/orders          # debe responder 401 (sin token)
```

Si responde **401**, el backend está funcionando: rechaza a quien no trae token, que es lo correcto.

> **Opcional pero recomendado:** poner contraseñas propias para Oracle. Crea `infra/apps/.env` en la EC2 (no se sube a git) copiando `infra/apps/.env.example` y cambia `ORACLE_ADMIN_PASSWORD` y `DB_PASSWORD`. Hazlo **antes** del primer `up`; si Oracle ya se creó, hay que borrar el volumen (`docker compose -f infra/apps/compose.yml down -v`).

---

## Paso 3 — Crear el API Gateway (HTTP API)

En la consola → **API Gateway → Crear API → HTTP API → Compilar**.

### 3.1 Integración y rutas

- Nombre de la API: `pedidos360-api`
- Integraciones: agrega **HTTP** con método **ANY** y URL `http://<ELASTIC_IP>:8080/api/orders`
- Rutas: `ANY /api/orders`
- Etapa: `$default` con **implementación automática** activada.

Crea la API y luego agrega la segunda ruta (**Rutas → Crear**):

| Ruta | Integración (HTTP, ANY) |
|---|---|
| `ANY /api/orders` | `http://<ELASTIC_IP>:8080/api/orders` |
| `ANY /api/orders/{proxy+}` | `http://<ELASTIC_IP>:8080/api/orders/{proxy}` |

La segunda ruta cubre `/api/orders/5`, `/api/orders/5/status`, etc.

### 3.2 Autorizador JWT (la validación del token de Azure)

**Autorización → Administrar autorizadores → Crear**:

| Campo | Valor |
|---|---|
| Tipo | JWT |
| Nombre | `azure-entra-id` |
| Origen de identidad | `$request.header.Authorization` |
| Emisor (Issuer) | `https://login.microsoftonline.com/9d7e1df5-4b2b-4f57-944c-0ea679424efd/v2.0` |
| Público (Audience) | `88e22461-0dce-4446-8509-c6eec7da2c02` |

Después, en **Adjuntar autorizadores a rutas**, asócialo a **las dos rutas** (`ANY /api/orders` y `ANY /api/orders/{proxy+}`).

### 3.3 CORS

**CORS → Configurar**:

| Campo | Valor |
|---|---|
| Access-Control-Allow-Origin | `http://localhost:5173` |
| Access-Control-Allow-Headers | `authorization, content-type` |
| Access-Control-Allow-Methods | `GET, POST, PUT, DELETE, OPTIONS` |

Guarda. Con CORS configurado, el Gateway responde solo los `OPTIONS` del navegador (sin pedir token).

---

## Paso 4 — Probar que funciona

Copia la **URL de invocación** del Gateway (algo como `https://abc123.execute-api.us-east-1.amazonaws.com`).

```bash
# Sin token: 401, y esta vez lo responde el propio API Gateway
curl -i https://<id>.execute-api.<region>.amazonaws.com/api/orders
```

Con token (Vicente te puede pasar uno desde la página de prueba o desde el front, botón "Copiar access token"):

```bash
TOKEN="eyJ..."   # no lo compartas en ningún lado, dura ~1 hora
curl -i -H "Authorization: Bearer $TOKEN" https://<id>.execute-api.<region>.amazonaws.com/api/orders
# 200 → []   (o 403 si el usuario no tiene rol)
```

| Resultado | Significa |
|---|---|
| 401 sin token | ✔ El Gateway protege la API |
| 200 con token | ✔ Todo el flujo funciona |
| 401 con token | El issuer o la audiencia del autorizador están mal escritos |
| 403 con token | El token es válido pero el usuario no tiene rol en Azure (lo arregla Vicente) |
| 503 / timeout | El Gateway no llega a la EC2: revisa la Elastic IP en la integración y el puerto 8080 del Security Group |

---

## Paso 5 — Conectar el frontend

Pásale a Vicente la URL de invocación. En el frontend se configura creando `frontend-pedidos360/.env.local`:

```
VITE_API_BASE_URL=https://<id>.execute-api.<region>.amazonaws.com
```

y reiniciando `npm run dev`. Desde ahí el front deja de hablarle directo al BFF y pasa por el Gateway.

---

## Evidencias para la defensa

Saca capturas de:

- La instancia `ec2-apps` corriendo, con su Security Group.
- `docker compose ps` con los tres contenedores arriba.
- El API Gateway: rutas, integraciones y el autorizador JWT con issuer/audience.
- El `curl` sin token (401) y con token (200).
- El front funcionando con la URL del Gateway.

---

## Cuando existan RabbitMQ y Kafka en el repo

El enunciado pide una VM por componente. Cuando estén `infra/mq/compose.yml` e `infra/kafka/compose.yml`:

| EC2 | Qué corre | Puertos a abrir (solo desde `sg-ec2-apps`) |
|---|---|---|
| `ec2-mq` | RabbitMQ (2 nodos) + panel de administración | 5672 (AMQP); 15672 (panel) solo desde tu IP |
| `ec2-kafka` | Zookeeper + Kafka + Kafka-UI | 9092 (Kafka); 8080 de Kafka-UI solo desde tu IP |

En cada una: instalar Docker igual que en el paso 2, `git clone` y `docker compose -f infra/<mq|kafka>/compose.yml up -d`. Después se agregan al Gateway las rutas de los nuevos microservicios (`/api/catalog/...`, `/api/report/...`, `/api/audit/...`) igual que en el paso 3.

---

## Comandos útiles en la EC2

```bash
cd ~/pedidos360-backend
git pull && docker compose -f infra/apps/compose.yml up -d --build   # actualizar a la última versión
docker compose -f infra/apps/compose.yml logs -f bff                 # ver logs del BFF
docker compose -f infra/apps/compose.yml restart                     # reiniciar todo
docker compose -f infra/apps/compose.yml down                        # apagar (los datos de Oracle se conservan)
```

Si al reiniciar el lab de AWS Academy la instancia estaba apagada: enciéndela, entra por SSH y corre el `up -d` de nuevo (Docker arranca solo, pero conviene revisar con `ps`).
