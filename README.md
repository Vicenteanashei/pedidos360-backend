# Pedidos360 backend

Backend Java 21 y Spring Boot para gestión de pedidos, catálogo, stock, notificaciones, comprobantes, reportes y auditoría. La interfaz está en [pedidos360-frontend](https://github.com/Vicenteanashei/pedidos360-frontend).

## Componentes

- BFF: entrada de la API y comunicación con los microservicios.
- Orders: pedidos, estados, reservas de stock, outbox, reportes y auditoría.
- Catalog: productos, existencias y operaciones de inventario.
- Notify: consumidores de correo, cocina y comprobantes; recibos idempotentes y artefactos. Un consumidor de las tres DLQ registra y archiva cada mensaje muerto (x-death incluido) antes del ACK; el Admin lo consulta en /api/notifications/dead-letters. `DLQ_LISTENER_ENABLED=false` deja los mensajes visibles en las DLQ.
- Rabbit Admin: administración del broker protegida por el rol Admin.
- p360-common: seguridad JWT, correlación y definición compartida de la topología.

Oracle (RDS for Oracle en AWS) conserva los datos por servicio, con un usuario por microservicio. RabbitMQ usa tres nodos, tres colas de tareas y tres DLQ, exchanges direct/topic y políticas DLX. Las claves exactas (email.send, kitchen.ticket, invoice.gen) se publican por cmd.direct; el aviso de cancelación usa email.send.high por cmd.topic. Los consumidores confirman después de completar su transacción y limitan los reintentos. Las colas son classic durables; tres nodos no equivalen a tres réplicas de cada mensaje.

## Compilar y probar

Requisitos: Java 21 y acceso a los repositorios Maven. Desde la raíz:

```bash
sh ms-pedidos360-orders/mvnw -f pom.xml package
```

## Configuración y despliegue

Crear el archivo privado de configuración y completar los valores propios:

```bash
cp infra/.env.example infra/.env
chmod 600 infra/.env
```

Configurar tenant y aplicación API de Entra, credenciales de Oracle/Rabbit, clave interna, orígenes CORS y direcciones privadas del broker. La API valida JWT, audiencia, scope y los roles Admin, Operador, Cliente y Auditor.

Los archivos Compose separan el host de aplicaciones (BFF y microservicios) y el host Rabbit; la base está en RDS y el frontend se publica desde su repositorio en S3 + CloudFront. Con Docker, Compose y Buildx disponibles, ejecutar en cada host correspondiente:

```bash
# Host Rabbit:
bash scripts/deploy-host.sh rabbit infra/.env
# Host backend, después de verificar Rabbit:
bash scripts/deploy-host.sh backend infra/.env
```

### Base de datos en RDS for Oracle

En AWS la base no corre en la EC2 de backend: cada microservicio usa su propio usuario en una instancia RDS for Oracle privada. Si `DB_URL` está definido en el `.env`, `deploy-host.sh` no levanta el contenedor Oracle; si está vacío, usa el contenedor local (perfil `local-db`) para desarrollo.

1. Crear el stack `infra/aws/rds-oracle-lab.json`: RDS for Oracle sin acceso público y un Security Group que solo acepta 1521 desde la EC2 backend. Su salida `DbUrl` es el valor de `DB_URL`.
2. En el `.env` del backend, definir `DB_URL=jdbc:oracle:thin:@//<endpoint-rds>:1521/<servicio>` y dejar `ORACLE_ADMIN_PASSWORD` vacío.
3. Crear los usuarios una vez, desde la EC2 backend (pide la contraseña maestra de forma oculta y no la guarda):

```bash
python3 -m pip install --user oracledb
python3 scripts/init-rds-users.py --env-file infra/.env --master-user admin
```

4. Desplegar con `bash scripts/deploy-host.sh backend infra/.env`. Flyway crea las tablas de cada servicio al iniciar.

Para instalaciones existentes, seguir primero la [migración de políticas Rabbit](docs/MIGRACION-POLITICAS-RABBIT.md). No sustituir volúmenes ni cambiar sus credenciales al actualizar las fuentes.

### Arquitectura AWS

| Plantilla (infra/aws) | Crea |
|---|---|
| `dos-instancias-lab.json` | EC2 de aplicaciones (BFF + microservicios) y EC2 Rabbit, con sus Security Groups |
| `rds-oracle-lab.json` | RDS for Oracle privado |
| `api-gateway-lab.json` | API Gateway HTTP API con JWT Authorizer de Entra, VPC Link y ALB interno hacia el BFF; CORS solo para el origen de CloudFront |
| `frontend-s3-cloudfront.json` (repo frontend) | Bucket S3 privado + CloudFront con Origin Access Control |

Orden: EC2 → RDS → frontend (para obtener su URL) → API Gateway con `FrontendOrigin` = URL de CloudFront. Las plantillas `tres-instancias-lab.json`, `web-sin-cloudfront-lab.json` y `web-existente-lab.json` corresponden a la versión anterior con frontend en EC2.

## Comprobaciones

El BFF expone /actuator/health. Los microservicios conservan sus endpoints de salud dentro de la red privada. La documentación OpenAPI mantiene protección de acceso.

En el host backend, con Management privado configurado:

```bash
python3 scripts/manual-rabbit-lab.py state
python3 scripts/manual-rabbit-lab.py --help
```

Los helpers manual-api y manual-session permiten probar la API desde terminal leyendo el access token por entrada oculta. Las operaciones de administración usan recursos demo.* para conservar las colas del caso. La inspección DLQ reencola el mensaje.

## Archivos privados

Mantener fuera de Git los archivos .env reales, tokens, contraseñas, respuestas de pruebas, capturas, datos y respaldos. Los hooks usan scripts/verify-public.py y requieren Gitleaks. Los ejemplos de configuración contienen valores por completar.

El archivo .eml acredita preparación del correo; la entrega externa requiere configurar SMTP. Los comprobantes PDF son académicos, sin validez tributaria.
