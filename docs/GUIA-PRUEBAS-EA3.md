# Guía para probar EA3 (Kafka, reportería y auditoría)

Dos formas de probar. La **A** no necesita credenciales ni internet y es la que se usó para las evidencias. La **B** usa Entra ID real y es la que corresponde a la demostración final.

## Requisitos
- Docker Desktop en ejecución (`docker version` debe mostrar Server).
- Java 21, Maven y Node 20+ (en este equipo están en `~/.local/share/cloud-native-toolchain`).
- Puertos libres: 8080–8088, 9092–9094, 9099.
- Compilar: `mvn -B -DskipTests package` en la raíz del backend.

## A. Prueba local sin credenciales

### 1. Clúster Kafka
```bash
# archivo privado fuera del repositorio con: KAFKA_UI_PASSWORD=<clave>
docker compose --env-file /ruta/kafka-local.env -p pedidos360-ea3-kafka -f infra/kafka/compose.yml up -d
docker ps --format '{{.Names}} {{.Status}}'     # 3 zookeeper + 3 kafka (healthy)
docker exec pedidos360-ea3-kafka-kafka1-1 kafka-topics --bootstrap-server localhost:19092 --describe --topic orders.events
```
Esperado: 3 particiones, ReplicationFactor 3, `Isr` con los tres brokers, `min.insync.replicas=2`. El contenedor `kafka-init` termina solo tras crear los 5 tópicos.

### 2. Emisor de tokens local (reemplaza a Entra)
```bash
node scripts/dev-issuer.mjs &                    # escucha en 127.0.0.1:9099
curl "http://127.0.0.1:9099/token?roles=Admin"   # imprime un JWT (1 h)
```
Roles disponibles: `Admin`, `Operador`, `Cliente`, `Auditor` (separados por coma). Solo funciona en localhost.

### 3. Servicios
Variables comunes (mismas para todos):
```bash
export ENTRA_ISSUER_URI=http://127.0.0.1:9099 API_CLIENT_ID=local-test \
       KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 RABBITMQ_PASSWORD=local-test \
       INTERNAL_SERVICE_KEY=$(openssl rand -hex 16)
```
Usar la **misma** `INTERNAL_SERVICE_KEY` en catalog, orders y bff. Ejecutar cada JAR desde su propia carpeta de trabajo (crea `./data` con H2):
```bash
java -jar ms-pedidos360-catalog/target/*-SNAPSHOT.jar      # 8082
java -jar ms-pedidos360-orders/target/*-SNAPSHOT.jar       # 8081
java -jar ms-pedidos360-report/target/*-SNAPSHOT.jar       # 8085
java -jar ms-pedidos360-audit/target/*-SNAPSHOT.jar        # 8086
java -jar ms-pedidos360-kafka-admin/target/*-SNAPSHOT.jar  # 8087
java -jar ms-pedidos360-bff/target/*-SNAPSHOT.jar          # 8080
```
Notas: sin RabbitMQ, `GET /actuator/health` de orders responde 503 (su health incluye Rabbit); las rutas de pedidos funcionan igual y los comandos Rabbit quedan pendientes en su outbox. Para probar también Rabbit, levantar `infra/mq/compose.yml`.

### 4. Recorrido de un pedido
```bash
T=$(curl -s "http://127.0.0.1:9099/token?roles=Admin")
H=(-H "Authorization: Bearer $T" -H "Content-Type: application/json")
curl "${H[@]}" -X POST localhost:8080/api/catalog/products -d '{"name":"Cafe","category":"Bebidas","price":10.00,"storeId":1,"stock":100,"active":true}'
curl "${H[@]}" -X POST localhost:8080/api/orders -d '{"storeId":1,"customerName":"Cliente","customerEmail":"c@test.local","deliveryAddress":"Calle 1","items":[{"productId":1,"quantity":2,"unitPrice":10.00}]}'
for s in ACEPTADO EN_PREPARACION DESPACHADO ENTREGADO; do
  curl "${H[@]}" -X PUT localhost:8080/api/orders/1/status -d "{\"status\":\"$s\"}"; done
sleep 10
curl "${H[@]}" "localhost:8080/api/audit?orderId=1"      # 5 eventos, uno por cambio
curl "${H[@]}" localhost:8080/api/report/kpis            # ventas, lead time, estados
curl "${H[@]}" localhost:8080/api/outbox/stream          # {"total":5,"pending":0}
```
Esperado: la auditoría lista los eventos con `eventVersion` creciente, los KPIs reflejan el pedido entregado y la outbox queda sin pendientes.

### 5. Roles y seguridad
| Token | `/api/audit` | `/api/reports` | `/api/kafka/*` |
| --- | --- | --- | --- |
| Admin | 200 | 200 | 200 |
| Auditor | 200 | 403 | 403 |
| Cliente | 403 | 403 | 403 |
| sin token | 401 | 401 | 401 |

### 6. Carga
```bash
export P360_API_URL=http://127.0.0.1:8080 P360_ACCESS_TOKEN=$(curl -s "http://127.0.0.1:9099/token?roles=Admin") \
       P360_PRODUCT_ID=1 P360_STORE_ID=1 P360_LOAD_COUNT=100
node scripts/stream-load.mjs
```
Entrega aceptados HTTP, latencia media/P95 y lag por grupo antes y después. El 201 HTTP no confirma Kafka: esperar ~20 s y comprobar lag 0 en los tres grupos y `pending: 0` en la outbox. Referencia de una ejecución local: 100/100 aceptados, media 33 ms, P95 46 ms, lag 0 tras drenar.

### 7. Resiliencia (para la demo)
- **Broker caído:** `docker stop pedidos360-ea3-kafka-kafka2-1`, describir el tópico (ISR 2, líderes reasignados), crear un pedido (se acepta), `docker start ...kafka2-1` y verificar el ISR completo.
- **Consumidor caído:** detener el JAR de report, crear pedidos, ver el lag subir en `p360-report` (Kafka UI o `/api/kafka/groups/p360-report`), reiniciarlo y verificar lag 0 sin duplicados en los KPIs.
- **Mensaje inválido:** publicar un JSON inválido con `kafka-console-producer` en `orders.events` (con clave) y comprobar que aparece en `orders.events.report.DLT` y `orders.events.audit.DLT` y que los siguientes eventos válidos se procesan.
- **Rebalanceo:** con `KAFKA_CONCURRENCY=1`, iniciar 2–3 instancias de report con `SERVER_PORT` distintos y ver el reparto de particiones; detener una y ver el rebalanceo.

### 8. Kafka UI
Abrir http://127.0.0.1:8088, usuario `kafkaadmin` y la clave del archivo privado. El clúster se llama **pedidos360**: Topics (particiones, RF, mensajes) y Consumers (lag por grupo).

### 9. Frontend
En `front/pedidos360-frontend`: `npm install` y `npm run build`. Para verlo con la API local hay que crear un `.env` con `VITE_API_BASE_URL=http://localhost:8080`; el login del frontend es con Entra real, así que las pantallas solo se prueban completas con la opción B.

### 10. Apagar y limpiar
```bash
docker compose -p pedidos360-ea3-kafka -f infra/kafka/compose.yml down        # conserva datos
docker compose -p pedidos360-ea3-kafka -f infra/kafka/compose.yml down -v     # BORRA los volúmenes
```
Detener los JAR y el emisor (`Stop-Process` o cerrar sus terminales). Las carpetas `data/` de H2 son desechables.

## AWS Academy sin CLI: desplegar Kafka desde la consola web

Requisitos: Learner Lab iniciado (círculo verde junto a AWS). Pulsar el botón verde **AWS** abre la consola con sesión federada. Región us-east-1.

1. **Datos de red del backend** (EC2 → Instancias → backend → Detalles/Seguridad): VPC ID, Subnet ID y el SG del backend (Security Groups, nombre `...BackendSG...`). Su IP pública para `SshSourceCidr`: https://checkip.amazonaws.com + `/32`.
2. **CloudFormation → Create stack → With new resources → Upload a template file** y subir `infra/aws/kafka-host-auto-lab.json`.
3. Nombre `pedidos360-ea3-kafka`. Parámetros: BackendSecurityGroupId, SubnetId, VpcId del paso 1; InstanceType `t3.large`; SshSourceCidr su IP/32; KafkaUiPassword alfanumérica de 16–64 caracteres (guardarla en un lugar privado); AmiId por defecto. Siguiente, siguiente, **Submit**. No crea roles IAM.
4. Al terminar (CREATE_COMPLETE), pestaña **Outputs**: copiar `KafkaPrivateIp` y `KafkaBootstrapServers`.
5. **Verificar sin entrar a la instancia**: EC2 → instancia `pedidos360-ea3-kafka` → Actions → Monitor and troubleshoot → **Get system log**. Esperar unos 4 minutos desde el arranque y buscar `[p360] LISTO`. Deben verse `kafka-init: exited 0`, 3 ZooKeeper y 3 brokers `healthy` y 5 tópicos con `PartitionCount: 3` y `ReplicationFactor: 3`. El botón de refrescar del log puede tardar: volver a abrir la página.
6. Añadir al `.env` del backend de EA2 (sin reemplazar nada existente): `KAFKA_BOOTSTRAP_SERVERS=<KafkaPrivateIp>:9092,<KafkaPrivateIp>:9093,<KafkaPrivateIp>:9094`, `REPORT_DB_PASSWORD`, `AUDIT_DB_PASSWORD` (32 caracteres hexadecimales cada una), `KAFKA_UI_USER` y `KAFKA_UI_PASSWORD`. Luego `bash scripts/deploy-host.sh backend <env>` en el host backend.

**Ahorro de créditos:** detenga la instancia con EC2 → Instance state → **Stop instance** cuando no la use (no cobra cómputo; solo el disco). Antes de presentar: Instance state → **Start instance**, esperar 2–3 minutos y comprobar. La IP privada se conserva al detener/iniciar, por lo que el `.env` del backend sigue siendo válido; la IP pública cambia, pero no se usa. No use **Terminate**: borra el disco y los tópicos. Para borrar todo al final: CloudFormation → Delete stack.

El Learner Lab consume presupuesto por las 3 instancias de EA2 y el balanceador aunque no se usen: detenerlas también entre sesiones si el saldo es ajustado.

## B. Prueba con Entra ID real
1. Completar `infra/.env` (`python3 scripts/configure-local.py`) con `ENTRA_TENANT_ID`, `API_CLIENT_ID`, `VITE_SPA_CLIENT_ID` y las contraseñas.
2. Frontend: `.env` con `VITE_ENTRA_TENANT_ID`, `VITE_SPA_CLIENT_ID`, `VITE_API_CLIENT_ID`, `VITE_API_BASE_URL`.
3. Los usuarios necesitan los roles de aplicación (`Admin`, `Auditor`, etc.) asignados en Entra y el scope `access_as_user` consentido.
4. Seguir los pasos 1, 3 (sin `ENTRA_ISSUER_URI`), 4, 6 y 7 usando el token real, o desde el propio frontend con sesión iniciada.
5. Para AWS y Oracle, seguir `docs/EA3-KAFKA.md` (hosts, `deploy-host.sh`, plantilla `kafka-host-lab.json`).

## Problemas conocidos
| Síntoma | Causa y solución |
| --- | --- |
| ZooKeeper `unhealthy` | Healthcheck antiguo con `cub zk-ready localhost 2181 5`; debe ser `localhost:2181`. Ya corregido en `compose.yml`. |
| Servicio no inicia: `INTERNAL_SERVICE_KEY` | Falta la variable en catalog/orders/bff. |
| 401 `Bearer token is malformed` | Token vacío: el emisor local no está corriendo. |
| Kafka UI queda cargando | La URL usa el nombre de clúster equivocado; es `pedidos360`. |
| `orders` health 503 | Rabbit no está levantado; no afecta a las rutas de pedidos. |
| `mq-init` falla con `set: pipefail` | Scripts con CRLF en Windows; el repositorio incluye `.gitattributes` para forzar LF. Si ya clonó, convertir con `sed -i 's/\r$//' infra/mq/*.sh infra/oracle/*.sh scripts/*.sh`. |
| ORA-01017 al conectar a Oracle | Los scripts de usuarios no corrieron (contenedor recreado durante su primera inicialización). Eliminar el volumen de prueba y esperar `healthy`. |
| `demo.probe` no existe | El auto-crear tópicos está desactivado a propósito; crearlos desde Kafka Admin (`demo.*`). |
