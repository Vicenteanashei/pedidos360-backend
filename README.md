# Pedidos360 backend

Backend Java 21 y Spring Boot para gestión de pedidos, catálogo, stock, notificaciones, comprobantes, reportes y auditoría. La interfaz está en [pedidos360-frontend](https://github.com/Vicenteanashei/pedidos360-frontend).

## Componentes

- BFF: entrada de la API y comunicación con los microservicios.
- Orders: pedidos, estados, reservas de stock, outbox, reportes y auditoría.
- Catalog: productos, existencias y operaciones de inventario.
- Notify: consumidores de correo, cocina y comprobantes; recibos idempotentes y artefactos.
- Rabbit Admin: administración del broker protegida por el rol Admin.
- p360-common: seguridad JWT, correlación y definición compartida de la topología.

Oracle conserva los datos por servicio. RabbitMQ usa tres nodos, tres colas de tareas y tres DLQ, exchanges direct/topic y políticas DLX. Los consumidores confirman después de completar su transacción y limitan los reintentos. Las colas son classic durables; tres nodos no equivalen a tres réplicas de cada mensaje.

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

Los archivos Compose separan los hosts backend/Oracle y Rabbit; el frontend tiene su propio repositorio y host. Con Docker, Compose y Buildx disponibles, ejecutar en cada host correspondiente:

```bash
# Host Rabbit:
bash scripts/deploy-host.sh rabbit infra/.env
# Host backend, después de verificar Rabbit:
bash scripts/deploy-host.sh backend infra/.env
```

Para instalaciones existentes, seguir primero la [migración de políticas Rabbit](docs/MIGRACION-POLITICAS-RABBIT.md). No sustituir volúmenes ni cambiar sus credenciales al actualizar las fuentes.

Las plantillas de infra/aws permiten separar frontend, backend y Rabbit en tres EC2. La opción web-sin-cloudfront-lab.json utiliza API Gateway HTTPS con ALB/VPC Link privados y autorización JWT para las rutas API. Configurar parámetros de la cuenta propia y registrar el redirect SPA en Entra.

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
