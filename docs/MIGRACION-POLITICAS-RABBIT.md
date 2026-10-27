# Migración a políticas DLX

Este procedimiento acompaña a scripts/migrate-rabbit-policies.py en instalaciones que todavía fijan x-dead-letter-* como argumentos de las colas. Las políticas no sustituyen esos argumentos: es necesario migrar antes de arrancar el backend nuevo. Una instalación nueva importa las políticas mediante mq-init y no necesita recrear colas anteriores.

## Condiciones

- Tres nodos activos sin particiones y las seis colas del proyecto existentes.
- Las principales deben ser classic, durables y no auto-delete.
- Cero conexiones AMQP al vhost y cero Ready, Unacked y Consumers en las tres principales.
- Sin bindings o políticas personalizadas que la migración no reconozca.

El script rechaza una migración parcial o con tareas pendientes. Guarda metadatos fuera de Git y solo recrea las principales que tengan argumentos antiguos, con condiciones if-empty/if-unused. No purga mensajes ni elimina DLQ, usuarios, credenciales o volúmenes. El respaldo de topología no es un respaldo de mensajes.

## Procedimiento por host

Construir y probar la versión nueva antes de detener los servicios. Conservar el proyecto Compose y los volúmenes existentes. Drenar las tareas pendientes con la versión anterior si hace falta.

En el host backend, desde la raíz del repositorio, completar RABBIT_MANAGEMENT_URL en el archivo privado con un origen Management accesible desde ese host, usando localhost o una IP privada. Conservar las credenciales existentes y las tres direcciones AMQP. El nombre interno rabbit1 solo resuelve dentro de su red Docker.

```bash
docker compose --env-file infra/.env -p pedidos360-ea2-backend \
  -f infra/backend/compose.yml build
docker compose --env-file infra/.env -p pedidos360-ea2-backend \
  -f infra/backend/compose.yml stop bff orders catalog notify rabbit-admin
python3 scripts/migrate-rabbit-policies.py --env-file infra/.env
```

Verificar que blockers esté vacío y guardar la línea base de las DLQ. Aplicar con respaldo fuera del repositorio:

```bash
python3 scripts/migrate-rabbit-policies.py --env-file infra/.env \
  --backup-dir "$HOME/.local/share/pedidos360/backups/rabbit-policy-migrations" --apply
```

En el host Rabbit, con los tres nodos saludables y la configuración nueva disponible:

```bash
docker compose --env-file infra/.env -p pedidos360-ea2-mq \
  -f infra/mq/compose.yml run --rm --no-deps mq-init
```

Volver al host backend para comprobar compatibilidad y arrancar solo las aplicaciones, conservando Oracle:

```bash
python3 scripts/migrate-rabbit-policies.py --env-file infra/.env --verify-ready
docker compose --env-file infra/.env -p pedidos360-ea2-backend \
  -f infra/backend/compose.yml up -d --no-build --no-deps bff orders catalog notify rabbit-admin
```

Verificar tres políticas, seis colas, nueve bindings, tres consumidores de tareas y salud de los servicios. Las principales deben mostrar su política efectiva sin argumentos x-dead-letter-* antiguos. Conservar los mensajes DLQ existentes.

Un fallo intenta restaurar la topología anterior. Si aparecieron mensajes nuevos y la protección impide revertir, mantener las aplicaciones detenidas y revisar el respaldo; no vaciar las colas ni arrancar una versión incompatible.
