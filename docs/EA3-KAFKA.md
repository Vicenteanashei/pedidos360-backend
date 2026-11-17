# EA3: Kafka, reportería y auditoría de Pedidos360

## Alcance

Se mantiene React por autorización del docente y Oracle en AWS. Java 21/Spring Boot 3.5 y los flujos RabbitMQ existentes se conservan.

Orders guarda un evento tipado y su cambio de negocio en la misma transacción Oracle, en P360_STREAM_OUTBOX. El publicador confirma Kafka antes de marcarlo publicado. Un fallo deja el evento pendiente con backoff; no se descarta al alcanzar un número arbitrario de intentos. Por pedido, una versión pendiente impide adelantar las siguientes.

Flujos:
- Orders → orders.events → grupo p360-report → modelos de lectura Report.
- Orders → orders.events → grupo p360-audit-ingest → registro durable pendiente.
- Audit → audit.timeline → grupo p360-audit-timeline → historial consultable.
- Orders → RabbitMQ → Notify, mediante la outbox de comandos anterior.

El envelope contiene eventId, type, action, timestamp UTC, correlationId, actorId, source, previousStatus y snapshot versionado. No envía nombre, correo, dirección ni notas del cliente a Kafka. Los snapshots permiten que Report ignore versiones antiguas y duplicadas. Audit deduplica por eventId y rechaza reutilizaciones con contenido distinto.

El desfase entre una operación y su proyección es eventual. El frontend consulta las proyecciones cada 15 segundos y muestra la fecha del último evento.

## Componentes

| Servicio | Puerto interno | Responsabilidad |
| --- | --- | --- |
| orders | 8081 | Operaciones, stock, outbox Rabbit y outbox Kafka |
| report | 8085 | KPIs, estados, ventas y productos desde proyecciones Kafka |
| audit | 8086 | Ingreso durable, publicación y lectura del timeline |
| kafka-admin | 8087 | Brokers, tópicos, configuraciones, consumer groups y offsets |
| BFF | 8080 | Entrada JWT y rutas hacia cada servicio |
| Kafka UI | 8088 del host por defecto | Administración independiente, con login |

Report usa agregaciones SQL sobre su esquema; no consulta el repositorio de Orders por HTTP ni carga todos los pedidos/líneas en memoria para calcular los KPIs. Las ventas se cuentan al entregar, y el lead time se calcula entre creación y entrega. Los buckets horarios incluyen el desfase UTC para distinguir horas repetidas en Chile. Si se cambia REPORT_TIME_ZONE, reconstruir los snapshots para recalcular esos buckets.

## Topología

infra/kafka/compose.yml configura tres ZooKeeper y tres brokers Confluent 7.9.2, con volúmenes separados. Esta versión conserva soporte ZooKeeper. No se sustituye por el laboratorio KRaft independiente.

| Tópico | Particiones | RF | Cleanup | Retención |
| --- | ---: | ---: | --- | --- |
| orders.events | 3 | 3 | delete | 7 días |
| audit.timeline | 3 | 3 | compact,delete | 30 días |
| orders.events.report.DLT | 3 | 3 | delete | 14 días |
| orders.events.audit.DLT | 3 | 3 | delete | 14 días |
| audit.timeline.timeline.DLT | 3 | 3 | delete | 14 días |

min.insync.replicas=2, acks=all e idempotencia del productor. La clave de orders.events es orderId; la de audit.timeline es eventId, para que compaction no borre eventos previos del mismo pedido.

Los consumidores validan el DTO y el total del snapshot. Los errores transitorios tienen dos reintentos con backoff de un segundo; los payloads inválidos pasan a su DLT. Si la publicación a DLT falla, el error no se considera recuperado. Los offsets se confirman después de que el procesador transaccional retorna.

Tres contenedores broker y tres ZooKeeper en una EC2 cumplen la topología lógica del laboratorio; no aportan tolerancia a la pérdida de esa EC2. acks=all/minISR=2 permite continuar escrituras con una réplica fuera de servicio, sujeto a ISR y conectividad; no promete escritura con dos brokers caídos.

## Preparación privada

1. Conservar una copia privada del .env y un backup offline de los volúmenes existentes.
2. Ejecutar python3 scripts/configure-local.py. Si existe infra/.env, agrega secretos faltantes sin reemplazar las credenciales existentes; si no existe, parte del ejemplo. Completar los IDs Entra y URLs propios. No publicar el archivo.
3. Completar direcciones privadas Kafka y Rabbit. Los brokers anuncian la IP privada del host Kafka, con puertos 9092/9093/9094; no anunciar localhost a servicios en otra EC2.
4. Opcionalmente generar archivos por host:

```bash
python3 scripts/prepare-cloud-env.py \
  --frontend-url https://ORIGEN_FRONTEND \
  --api-url https://ORIGEN_API \
  --rabbit-ip IP_PRIVADA_RABBIT \
  --kafka-ip IP_PRIVADA_KAFKA \
  --output /ruta/privada/configuracion
```

La herramienta produce backend.env, frontend.env, rabbit.env y kafka.env. No imprime contraseñas ni los despliega.

## Host Kafka en AWS

infra/aws/kafka-host-lab.json permite preparar una EC2 adicional, sin modificar la plantilla de las tres EC2 de EA2. Requiere VPC, subnet con salida a Internet, SG del backend y CIDR administrativo. El host dispone de Docker y Compose; el despliegue de fuentes/configuración se realiza después. Revisar capacidad/costos de la cuenta antes de crear recursos. El volumen raíz se elimina al borrar la instancia: guardar backup antes de eliminar el stack.

Usar un host de al menos 8 GiB de RAM para este laboratorio. Permitir TCP 9092–9094 únicamente desde el SG del backend. ZooKeeper no publica puertos al host. Kafka UI se enlaza a 127.0.0.1 y requiere credenciales propias. En el host autoconfigurable (sin llave SSH) no hay túnel SSH posible: para una demostración se abre de forma temporal y restringida (se enlaza a 0.0.0.0 y el SG admite el puerto 8088 solo desde la IP /32 del presentador), y se vuelve a cerrar al terminar: en /opt/pedidos360/kafka poner KAFKA_UI_BIND_ADDRESS=0.0.0.0 en kafka.env, ejecutar `docker compose --env-file kafka.env -p pedidos360-ea3-kafka -f compose.yml up -d --no-deps kafka-ui` y abrir el puerto 8088 en el SG; al terminar, revertir ambos cambios. Para ejecutar comandos en el host se usa EC2 Instance Connect desde la consola, con una regla SSH desde 18.206.107.24/29. La comunicación Kafka PLAINTEXT queda dentro de la red privada; si el escenario exige TLS/SASL, configurarlo antes de ampliar su exposición.

En el host Kafka, con kafka.env transferido aparte:

```bash
bash scripts/deploy-host.sh kafka /ruta/privada/kafka.env
docker compose --env-file /ruta/privada/kafka.env -p pedidos360-ea3-kafka -f infra/kafka/compose.yml ps -a
```

kafka-init crea y valida tópicos. No borra datos ni cambia RF silenciosamente. Si detecta un tópico existente con otra cantidad de particiones/RF, se detiene y exige una reconciliación explícita. Un cambio de RF requiere reasignación de réplicas, no crear de nuevo el tópico.

### Variante autoconfigurable (sin SSH ni llaves)

infra/aws/kafka-host-auto-lab.json crea la misma EC2, pero su UserData instala Docker, escribe infra/kafka/compose.yml e init-topics.sh, genera kafka.env con la IP privada de la instancia y levanta el clúster al primer arranque. No requiere llave SSH, rol IAM ni copiar archivos. Se genera con `node scripts/build-kafka-auto-template.mjs` a partir de los archivos del repositorio; si cambia infra/kafka, volver a generarla.

Parámetros: VpcId, SubnetId, BackendSecurityGroupId, SshSourceCidr (no puede ser 0.0.0.0/0), InstanceType (t3.large), AmiId y KafkaUiPassword (16–64 caracteres alfanuméricos, NoEcho). La clave queda en el UserData y en /opt/pedidos360/kafka/kafka.env de la instancia (modo 600): es adecuada para un laboratorio, no para producción. Salidas: KafkaPrivateIp, KafkaBootstrapServers, KafkaInstanceId y KafkaSecurityGroupId.

Verificación sin acceso a la instancia: EC2 → instancia → Actions → Monitor and troubleshoot → Get system log. Las líneas con `[p360]` muestran el estado de los contenedores y el `describe` de los tópicos, y terminan con `[p360] LISTO`. El mismo registro queda en /var/log/pedidos360-kafka-boot.log.

Al detener y volver a iniciar la instancia, Docker reinicia los contenedores (`restart: unless-stopped`) y la IP privada se conserva. Detenerla entre sesiones evita el costo de cómputo; los volúmenes y los tópicos permanecen. Un stack eliminado borra el disco y los datos.

Los servicios del backend se apuntan al clúster con KAFKA_BOOTSTRAP_SERVERS=<KafkaPrivateIp>:9092,<KafkaPrivateIp>:9093,<KafkaPrivateIp>:9094 en el .env del backend.

## Backend Oracle existente

En el .env del backend agregar REPORT_DB_PASSWORD, AUDIT_DB_PASSWORD y KAFKA_BOOTSTRAP_SERVERS. Conservar todas las claves previas.

```bash
bash scripts/deploy-host.sh backend /ruta/privada/backend.env
```

El script valida Rabbit, inicia Oracle con el volumen existente y ejecuta 02-stream-users.sh. Este crea P360_REPORT y P360_AUDIT solo si faltan, sin cambiar contraseñas de usuarios existentes. Flyway V3 de Orders agrega contador/outbox; los esquemas Report/Audit tienen sus propias migraciones.

No usar docker compose down -v para actualizar. No sustituir oradata ni ejecutar el script inicial de usuarios de EA2 sobre una base ya instalada.

## Migración de pedidos anteriores

Los pedidos existentes no tienen eventos Kafka históricos. Para poblar el estado actual de Report/Audit, un Admin puede llamar al BFF:

- GET /api/outbox/stream: total y cantidad pendiente.
- POST /api/outbox/stream/replay?page=0&size=100: encola snapshots versionados.
- Continuar con page=1, etc., mientras hasNext sea true.

Cada snapshot se registra como OrderSnapshot/SNAPSHOT y con el actor que inició la reconstrucción. No se inventan los eventos ni los actores históricos. El historial SQL anterior de Orders se conserva; no se migra automáticamente como si hubiera sido recibido desde Kafka.

Durante la reconstrucción, las actualizaciones normales siguen usando el bloqueo del pedido y versiones crecientes. Un snapshot antiguo no pisa una versión más reciente ya proyectada.

## API y seguridad

Todas las rutas API siguen requiriendo scope access_as_user y JWT con issuer/audience válidos. Los nuevos servicios vuelven a comprobar roles.

- Admin: GET /api/reports?range=last24h|last7d|all.
- Admin: GET /api/report/kpis y /api/report/top-products.
- Admin/Auditor: GET /api/audit?page=0&size=100&orderId=...&actorId=...&type=...&from=...&to=....
- Admin: GET /api/kafka/cluster y /api/kafka/topics.
- Admin: GET/PUT /api/kafka/topics/{topic}/config.
- Admin: POST /api/kafka/topics; DELETE /api/kafka/topics/{topic}.
- Admin: GET /api/kafka/groups y /api/kafka/groups/{group}.
- Admin: POST /api/kafka/groups/{group}/offsets.

Crear/eliminar tópicos desde el microservicio se limita a demo.*. Los tópicos de negocio/DLT están protegidos contra borrado y cambios incompatibles con la topología. Se pueden cambiar retenciones dentro de límites. Los grupos administrables son p360-* y demo.*; cambiar offsets exige ausencia de miembros activos y valores dentro del log retenido. Adelantar un offset omite eventos; retroceder puede reprocesarlos.

Kafka UI es independiente del microservicio administrador; mostrar ambos durante la evaluación.

## Verificación reproducible

```bash
# Java 21:
sh ms-pedidos360-orders/mvnw -f pom.xml package
python3 -m unittest discover -s scripts -p 'test_*.py'

# Con variables privadas cargadas por Compose; no imprimir config resuelta:
docker compose --env-file /ruta/privada/kafka.env -f infra/kafka/compose.yml config -q
docker compose --env-file /ruta/privada/backend.env -f infra/backend/compose.yml config -q
```

Las pruebas Java incluyen:
- Rechazo de pedidos inválidos sin evento parcial; snapshot sin datos de contacto.
- Fallo/confirmación Kafka y orden de versiones pendientes, preservando el pedido.
- Report idempotente y resistente a eventos fuera de orden.
- Auditoría invisible hasta consumir timeline, historial completo y deduplicación.
- Broker Kafka embebido: deserialización inválida a DLT y continuación del flujo válido.
- Broker embebido: publicación y consumo de ambos tópicos y claves únicas de timeline.
- Permisos API y protección de tópicos/configuración.

El broker embebido usa RF=1 para probar integración sin Docker. No acredita el arranque del clúster Docker RF=3, su conmutación de líderes, Oracle real ni el despliegue AWS. Esas verificaciones requieren el entorno de despliegue.

## Evidencias en el entorno de evaluación

1. Tres brokers y ZooKeeper disponibles; describe de tópicos con 3 particiones/RF=3 e ISR.
2. Login por rol y acceso al BFF.
3. Crear/aceptar/preparar/despachar/entregar un pedido; observar eventos y modelos de lectura.
4. Mostrar mensajes en los dos tópicos y progreso de los tres grupos.
5. Detener Audit o Report, producir eventos, observar lag y recuperación sin duplicados.
6. KAFKA_CONCURRENCY=1 en el .env del backend y dos/tres instancias de Report para mostrar reparto/rebalanceo; no publicar puertos adicionales del servicio.
7. Evento inválido a DLT y continuación del siguiente evento válido.
8. Crear, modificar e inspeccionar demo.* por API; demostrar protección de tópicos del caso.
9. Mostrar Kafka UI (acceso temporal restringido a la IP del presentador, descrito en la sección «Host Kafka en AWS»).
10. Confirmar que Rabbit, DLQ, notificaciones, stock y roles anteriores siguen funcionando.

Para una carga explícita de laboratorio, scripts/stream-load.mjs lee P360_API_URL, P360_ACCESS_TOKEN, P360_PRODUCT_ID, P360_STORE_ID y P360_LOAD_COUNT del entorno privado. No poner el token en argumentos ni guardar la salida de un entorno que lo imprima. Inicia con 20 pedidos; el máximo está limitado a 5000 y la concurrencia HTTP a cinco. Solo ejecutarlo contra datos de prueba.

El script registra latencia HTTP media/P95, solicitudes aceptadas y lag por grupo antes/después. No presenta HTTP 201 como confirmación Kafka. Registrar también el drenaje de la outbox, el fin de log/offset confirmado y las proyecciones. Las métricas y capturas deben provenir de una ejecución real; no se incluyen resultados inventados.

