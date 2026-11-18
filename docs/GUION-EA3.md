# Guion para demostrar Kafka en Pedidos360 · EA3

La demostración principal dura unos 8 a 10 minutos. Usa un pedido de prueba de principio a fin para mostrar que el mismo cambio llega a Reportes, Auditoría y, cuando corresponde, al flujo de tareas de RabbitMQ.

## Antes de presentar

1. En AWS Academy, inicia **Learner Lab** y abre la consola AWS en `us-east-1`. En **EC2 → Instances**, selecciona `pedidos360-ea3-kafka` y `pedidos360-ea2-rabbit` y elige **Instance state → Start instance**. Después inicia `pedidos360-ea2-backend` y `pedidos360-ea2-frontend`. Espera los chequeos de EC2 y confirma en la aplicación que ya aparecen los tres brokers.
2. En **CloudFormation → pedidos360-ea2-https → Outputs**, copia `FrontendUrl`. Abre la aplicación e inicia sesión con tu cuenta Entra de rol **Admin**. Ten a mano un local con un producto activo y stock disponible. Usa un nombre y un correo ficticios en el pedido de prueba.
3. Abre **Pedidos**, **Reportes**, **Auditoría** y **Kafka**. Si vas a mostrar los mensajes del broker en vivo, prepara también el acceso restringido a **Kafka UI** antes de compartir pantalla. La herramienta tiene su propio inicio de sesión. [Acceso a Kafka UI](EA3-KAFKA.md#host-kafka-en-aws)

## Demostración frente al profesor

### 1. Presenta el clúster

En **Kafka → Tópicos**, señala **Brokers disponibles: 3** y las filas `orders.events` y `audit.timeline`. Ambos tienen **3 particiones**, **3 réplicas por partición** y, en condiciones normales, **ISR 3/3 por partición**. En **Configuración** de `orders.events`, muestra la retención de 7 días y el mínimo de 2 réplicas en sincronía. El Compose del caso también levanta tres nodos ZooKeeper.

> «Las particiones permiten repartir el trabajo. Las réplicas mantienen copias de cada partición. ISR es el conjunto de réplicas al día. Aquí los tres brokers están en una EC2, así que una caída de esa máquina afecta a todo el clúster».

Si el profesor pide ver ZooKeeper, muestra `zookeeper1`, `zookeeper2` y `zookeeper3` en el [Compose de Kafka](../infra/kafka/compose.yml) o ejecuta esto en la EC2 Kafka con acceso a Docker:

```bash
docker ps --filter label=com.docker.compose.project=pedidos360-ea3-kafka \
  --format 'table {{.Names}}\t{{.Status}}'
```

### 2. Crea un pedido y llévalo a entregado

1. Antes de crearlo, en **Reportes** selecciona **24 horas** y apunta los entregados y las ventas como referencia. En **Kafka → Grupos de consumidores**, abre `p360-report` y observa los offsets y el lag por partición.
2. En **Pedidos → + Nuevo pedido**, elige el local, completa los datos ficticios, selecciona un producto con stock y pulsa **Crear pedido**. Anota el **ID** y el **total**.
3. Abre el pedido y usa **Aceptar → Pasar a preparación → Despachar → Marcar entregado**. El recorrido genera `OrderCreated`, `OrderAccepted`, `OrderPreparing`, `OrderDispatched` y `OrderDelivered`.

> «Orders guarda el cambio y el evento pendiente en la misma transacción de Oracle. Un publicador de la outbox lo envía a Kafka. El refresco de Reportes y Auditoría puede tardar porque ambos consumen de forma asíncrona».

### 3. Sigue ese pedido por Kafka y por la aplicación

1. En **Kafka UI → pedidos360 → Topics → orders.events → Messages**, consulta los mensajes recientes. Identifica `OrderDelivered` con `payload.id` igual al ID que anotaste y señala `eventId`, `type` y `payload.version`. Muestra también **`audit.timeline`**. En `orders.events` la clave es `orderId`, lo que mantiene juntos y ordenados los cambios de ese pedido dentro de una partición. En `audit.timeline` la clave es `eventId`, para que la compactación conserve los cambios distintos del mismo pedido.
2. En **Auditoría → Pedido**, escribe el ID y pulsa **Aplicar filtros**. Si hace falta, espera el refresco automático de 15 segundos o pulsa **Actualizar**. Muestra los **cinco eventos**, el actor, la fecha y la transición de estado.
3. En **Reportes → Actualizar**, muestra el dato de **Último evento procesado**, el aumento en **Pedidos entregados** y **Ventas entregadas**, y el gráfico **Lead time promedio por día de entrega**. Lead time es el tiempo entre crear y entregar el pedido.
4. En **Kafka → Grupos de consumidores**, abre `p360-report`, `p360-audit-ingest` y `p360-audit-timeline`. Usa **Actualizar grupo** para consultar sus posiciones. Al terminar el procesamiento, espera **lag 0** en las particiones con offsets. El lag es la diferencia entre el final del log y el offset confirmado por ese grupo.

> «Report y Audit leen `orders.events` con grupos diferentes, cada uno con su propia posición. Report calcula sus indicadores. Audit publica en `audit.timeline` y su grupo de timeline materializa el historial que acabamos de consultar».

### 4. Demuestra la administración de Kafka

1. En **Kafka → Crear tópico de prueba**, escribe `demo.defensa` o un nombre `demo.*` disponible. Deja **3** particiones, **3** réplicas y **7** días, y pulsa **Crear tópico**.
2. Abre **Configuración**, cambia la retención a **3 días** y pulsa **Guardar configuración**. Debe mostrar `259200000` ms. Pulsa **Restablecer retención** y confirma para volver al valor del clúster. Si te piden demostrar la otra opción, cambia **Limpieza** y usa **Restablecer limpieza**.
3. Antes de eliminarlo, actualiza la lista en **Kafka UI → Topics** y abre `demo.defensa` para ver sus particiones y su configuración. Luego elimina el tópico de prueba desde Pedidos360. Señala que **Eliminar** aparece deshabilitado para `orders.events`. **Administración Kafka** es la pantalla de Pedidos360 que usa el microservicio y **Kafka UI** es la herramienta independiente que inspecciona mensajes, brokers y grupos.

### 5. Conecta la demo con RabbitMQ

En **Notificaciones**, pulsa **Actualizar** y busca el mismo ID. Señala los trabajos que aparezcan, como correo preparado, ticket de cocina o comprobante. El `.eml` acredita la preparación del correo. En **RabbitMQ**, puedes mostrar `q.cmd.email`, `q.cmd.kitchen`, `q.cmd.invoice` y sus DLQ.

> «Cuando la aplicación pide preparar un correo o generar un documento, manda un comando a RabbitMQ. Cuando registra que el pedido fue entregado, publica un evento en Kafka. Reportes y Auditoría pueden leer ese hecho y usarlo para fines distintos».

## Prueba adicional · pausar Report y mostrar cómo se pone al día

Hazla al final, con acceso a la terminal de la EC2 **backend** preparado. Deja Kafka, Orders y Audit funcionando. Si tienes disponible solo la interfaz, la sección «Recuperación de consumidor» en `revision-ea3/EVIDENCIAS-LOCALES.md` documenta la prueba local.

1. Crea un segundo pedido de prueba y déjalo en **Creado**. Espera a que aparezca en Auditoría y comprueba que `p360-report` está al día con **Actualizar grupo**. Anota los conteos de **Creado** y **Aceptado** en Reportes. Así ya existe un offset confirmado en la partición que recibe los eventos de ese pedido. Desde la EC2 backend, identifica el contenedor de Report y sustituye `NOMBRE_DE_REPORT` por el nombre devuelto. Si la sesión usa `ec2-user` sin acceso a Docker, antepone `sudo`.

   ```bash
   docker ps --filter label=com.docker.compose.project=pedidos360-ea2-backend \
     --filter label=com.docker.compose.service=report --format '{{.Names}}'
   docker stop NOMBRE_DE_REPORT
   ```

2. Pulsa **Aceptar** en el segundo pedido. Actualiza `p360-report` y muestra que crece el lag en la partición que recibió el evento. En Auditoría, muestra que `OrderAccepted` ya aparece para ese ID.
3. Ejecuta `docker start NOMBRE_DE_REPORT`. Actualiza el grupo hasta que el lag vuelva a **0**. En **Reportes → Estados actuales de pedidos**, comprueba que hay un **Creado** menos y un **Aceptado** más respecto a la cifra que había al pausar Report. Las versiones del snapshot evitan que un evento repetido cuente dos veces o que uno antiguo pise un estado más nuevo. Deja Report iniciado al terminar.

Para preparar un ejercicio de DLT, rebalanceo o caída de un broker, usa la [guía de pruebas de EA3](GUIA-PRUEBAS-EA3.md#7-resiliencia-para-la-demo). Los mensajes inválidos se pueden mostrar con las evidencias locales de las DLT de Report y Audit.

Si te piden enseñar el replay, la demo de `revision-ea3/material-clases` permite publicar tres registros en `pedidos.demo`, leerlos con `reportes`, volver a leerlos con `auditoria` y consultarlos de nuevo desde el inicio. Hazlo con Docker Desktop como ejercicio independiente, siguiendo su `README.md`. En AWS puedes mostrar que el mensaje sigue visible en Kafka UI después de que los grupos lo procesaron.

## Por qué se implementó Kafka y cómo se compara con RabbitMQ

El EFT asigna **los comandos de notificación a RabbitMQ** y **los eventos de analítica y auditoría a Kafka**. En Pedidos360, publicar una vez en `orders.events` permite que Report y Audit procesen a ritmos distintos. Los eventos se conservan tras la lectura y un nuevo grupo puede volver a leer lo que sigue retenido. Es el mismo principio que muestra el material de clase con dos grupos, `reportes` y `auditoria`, leyendo `pedidos.demo`.

| Aspecto | Kafka en Pedidos360 | RabbitMQ en Pedidos360 |
| --- | --- | --- |
| Mensaje típico | Evento que describe algo que pasó, como `OrderDelivered`. | Comando que pide hacer algo, como `invoice.gen`. |
| Distribución | Los grupos leen el mismo tópico de forma independiente. Dentro de un grupo, los miembros se reparten las particiones. | Un exchange enruta el comando según su routing key. Los consumidores de una cola se reparten sus entregas. Se puede enrutar un mensaje a varias colas. |
| Confirmación y relectura | El consumidor confirma su offset. El evento sigue en el tópico según su retención y política de limpieza. | El consumidor envía un ACK. En las colas usadas aquí, el broker retira esa entrega confirmada de la cola. |
| Fallos | Un registro que no se puede procesar puede acabar en un tópico DLT del consumidor. | Un mensaje que no se puede procesar puede acabar en una cola DLQ. |
| Mejor encaje | Historial compartido, replay durante la retención y procesamiento paralelo de flujos de eventos. | Reparto de tareas y enrutamiento flexible con exchanges, bindings y colas. |
| Qué administrar | Particiones, retención, replicación y progreso por grupo. | Exchanges, bindings, colas, confirmaciones y políticas de reintento. |

Ambos desacoplan productores y consumidores, permiten procesar de forma asíncrona y pueden entregar mensajes de nuevo después de fallos. Por eso el consumidor debe tolerar duplicados. En Pedidos360, Report usa versiones y Audit deduplica por `eventId`. La comparación se refiere a las colas clásicas del proyecto. RabbitMQ también ofrece Streams para retención y relectura, y colas quorum replicadas.

Kafka puede manejar flujos grandes mediante particiones. RabbitMQ simplifica el enrutamiento de tareas a colas específicas. El rendimiento depende de la carga, la configuración y el hardware. Como referencia del recorrido en AWS, se registraron **20 de 20 pedidos HTTP aceptados, P95 HTTP de 775 ms**, seguidos de **lag 0** y **outbox sin pendientes**. Para comparar el rendimiento de los brokers haría falta una prueba específica con la misma carga y condiciones. Los resultados están en `revision-ea3/EVIDENCIAS-AWS.md`.

### Respuesta de 30 segundos

> «Implementamos Kafka porque un mismo hecho del pedido lo necesitan Reportes y Auditoría. Cada servicio lee los mismos eventos a su ritmo y puede volver a leer el historial disponible. RabbitMQ sigue resolviendo los trabajos individuales, como preparar un correo o generar un comprobante. Kafka encaja mejor con el historial de eventos y RabbitMQ con el enrutamiento de las tareas».
