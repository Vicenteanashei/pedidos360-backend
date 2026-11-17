# Guion de presentación EA3 (Kafka, reportería y auditoría)

Duración sugerida: 10–12 min. Cada paso indica qué mostrar y qué decir. Las capturas y métricas deben provenir de una ejecución real (ver «Evidencias» en EA3-KAFKA.md).

## 1. Arquitectura (1 min)
- Mostrar el diagrama del caso: Orders publica en Kafka (Report y Audit) y en RabbitMQ (Notify).
- Decir: RabbitMQ conserva tareas/notificaciones; Kafka transporta eventos de negocio para analítica y auditoría. React y Oracle en AWS se mantienen por autorización del docente.

## 2. Clúster Kafka (2 min)
- `docker compose -p pedidos360-ea3-kafka -f infra/kafka/compose.yml ps -a`: 3 ZooKeeper, 3 brokers, Kafka UI.
- Describe de `orders.events` y `audit.timeline`: 3 particiones, RF=3, ISR completo, `min.insync.replicas=2`.
- Decir: claves distintas (orderId vs eventId) y por qué compaction en timeline no borra eventos previos del pedido.

## 3. Flujo extremo a extremo (3 min)
- Login como Admin. Crear, aceptar, preparar, despachar y entregar un pedido.
- Mostrar el mensaje en `orders.events` (Kafka UI) y los 3 grupos: `p360-report`, `p360-audit-ingest`, `p360-audit-timeline`.
- Mostrar Reportes (KPIs, estados, productos) y Auditoría (historial del pedido) actualizados.
- Decir: outbox transaccional, publicación confirmada antes de marcar publicado, consistencia eventual (frontend consulta cada 15 s).

## 4. Resiliencia (2 min)
- Detener Report, crear pedidos, mostrar lag creciendo; reiniciar y mostrar recuperación sin duplicados (versiones del snapshot).
- Enviar un evento inválido: va a su DLT y el siguiente evento válido se procesa.
- Decir: 2 reintentos con backoff de 1 s, `IllegalArgumentException` no reintentable, offsets confirmados tras el procesamiento.

## 5. Consumer groups (1 min)
- Con `KAFKA_CONCURRENCY=1`, levantar 2–3 instancias de Report: mostrar reparto de particiones y rebalanceo al detener una.

## 6. Administración (2 min)
- Kafka Admin (pantalla Kafka del frontend, rol Admin): clúster, tópicos, configuración, grupos y offsets.
- Crear, modificar y borrar `demo.*`; intentar borrar `orders.events` → rechazado (403).
- Kafka UI por túnel: es una herramienta independiente del microservicio.

## 7. No regresión (1 min)
- Rabbit, DLQ, notificaciones, stock y roles anteriores siguen funcionando.

## Preguntas probables
- ¿Por qué ZooKeeper y no KRaft? La pauta lo exige; la demo KRaft de clases es un laboratorio independiente.
- ¿Tolerancia a fallos real? Tres brokers en una EC2 cumplen la topología lógica, no sobreviven a la pérdida del host.
- ¿Por qué React? El docente autorizó mantenerlo.
