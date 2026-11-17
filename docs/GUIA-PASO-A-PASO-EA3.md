# Guía paso a paso EA3: del estado actual a la presentación

Estado de partida (17-11-2026): Kafka ya está desplegado y verificado en AWS (EC2 `pedidos360-ea3-kafka`, IP privada **<IP_PRIVADA_KAFKA>**, hoy **detenida**). El código de Report, Audit y Kafka Admin está listo y probado en local, pero **no está desplegado en el backend de EA2, no está en Git y no se ha probado con el login real de Entra**. Esta guía cubre lo que falta.

Tiempos estimados entre paréntesis. Marque cada casilla al terminarla.

## Datos que necesita a mano

| Dato | Valor |
| --- | --- |
| Región | us-east-1 |
| Backend EA2 | `pedidos360-ea2-backend`, t3.large, IP privada <IP privada del backend>, SG `<SG del backend>` |
| Rabbit EA2 | `pedidos360-ea2-rabbit`, t3.small, IP privada <IP privada de Rabbit> |
| Frontend EA2 | `pedidos360-ea2-frontend`, t3.micro |
| Kafka EA3 | `pedidos360-ea3-kafka`, t3.large, IP privada <IP_PRIVADA_KAFKA>, SG `<SG de Kafka>` |
| URL de frontend y API (mismo origen) | https://<ID>.execute-api.us-east-1.amazonaws.com |
| Redirect de Entra | https://<ID>.execute-api.us-east-1.amazonaws.com/redirect.html |
| Valores reales de esta tabla | Archivo privado `~/.local/share/cloud-native-toolchain/aws-env/datos-aws-ea3.txt` (no se publica); las IP privadas también salen de EC2 → Detalles de cada instancia, y la URL de la app de CloudFormation → stack `pedidos360-ea2-https` → Outputs |
| Claves nuevas para el backend | `~/.local/share/cloud-native-toolchain/aws-env/backend-ea3-agregar.env` |
| Clave de Kafka UI | `~/.local/share/cloud-native-toolchain/kafka-ui-aws-credenciales.txt` |

Los IDs de Entra (tenant, cliente de la API y cliente del SPA) salen del portal de Azure → Registros de aplicaciones. No son secretos. Ya los usó en EA2: están en el `.env` actual del backend (`ENTRA_TENANT_ID`, `API_CLIENT_ID`, `SPA_CLIENT_ID`).

## Reglas para no perder créditos ni datos

- Saldo del Learner Lab: 50 USD, 30,3 usados al 17-11. Hay que dejar **al menos 12–15 USD** para la presentación.
- Cada día encendidas, las instancias cuestan aproximadamente: Kafka t3.large 2 USD, backend t3.large 2 USD, Rabbit 0,5 USD, frontend 0,25 USD, más el balanceador interno (0,5 USD). Detenga lo que no use.
- Nunca use **Terminate** (borra el disco) ni `docker compose down -v` (borra los volúmenes de Oracle y Kafka).
- Antes de tocar el backend, haga backup (Paso 6).

---

## Parte 1. Preparar el código (en su PC)

### Paso 1. Revisar los cambios (5 min)
- [ ] En `back+/pedidos360-backend`: `git status` debe mostrar los cambios de EA3 (servicios `ms-pedidos360-report`, `ms-pedidos360-audit`, `ms-pedidos360-kafka-admin`, módulo `p360-stream`, `infra/kafka`, `infra/aws/kafka-host-*.json`, scripts y documentación).
- [ ] En `front/pedidos360-frontend`: `git status` debe mostrar `KafkaComponent.jsx`, `usePollingResource.js` y los cambios de Reportes y Auditoría.

### Paso 2. Commit y push (5 min)
Pida a Claude que haga el commit, o ejecute usted en cada repositorio, con mensajes Conventional Commits:

```bash
# backend
git add -A
git commit -m "feat(kafka): reporteria y auditoria con Kafka, kafka-admin y despliegue AWS"
git push

# frontend
git add -A
git commit -m "feat(ui): pantallas de Kafka, reportes y auditoria"
git push
```
Los hosts de AWS tomarán el código desde GitHub (`git clone`/`git pull`). Si sus repositorios son privados, use el método de paquete de EA2 (Paso 5, opción B).

---

## Parte 2. Encender la infraestructura (día de la presentación, o el día previo para ensayar)

### Paso 3. Iniciar el laboratorio y la consola (3 min)
- [ ] Canvas → su curso → Learner Lab → **Start Lab**. Esperar el círculo verde.
- [ ] Pulsar el botón verde **AWS** (abre la consola con sesión). Comprobar el saldo en "Used $X of $50".
- [ ] EC2 → Instancias: iniciar con **Instance state → Start instance** las que estén detenidas: backend, Rabbit, frontend y **Kafka**.
- [ ] Esperar 3 minutos. Cuando las instancias se reinician **las IP públicas cambian**; las privadas no. Si el frontend usa una IP pública fija en alguna configuración, ajustarla.

### Paso 4. Verificar Kafka (3 min)
- [ ] EC2 → `pedidos360-ea3-kafka` → Actions → Monitor and troubleshoot → **Get system log**. Pulse el botón de refrescar del log y vuelva a abrir la página si aparece vacío.
- [ ] Debe verse que los contenedores arrancaron. Como en el reinicio no se repite el resumen `[p360]`, la comprobación fuerte la hará el backend al conectar (Paso 8) y Kafka UI (Paso 11).
- [ ] Si Kafka no levantó: reiniciar la instancia (Instance state → Reboot) y esperar 3 minutos. Como último recurso, eliminar el stack y recrearlo con `infra/aws/kafka-host-auto-lab.json` (ver `docs/GUIA-PRUEBAS-EA3.md`); la IP privada cambia y debe actualizar `KAFKA_BOOTSTRAP_SERVERS`.

---

## Parte 3. Actualizar el backend de EA2

### Paso 5. Obtener el código en el host backend (10 min)
Entre al host: EC2 → `pedidos360-ea2-backend` → **Connect → EC2 Instance Connect → Connect**.

- **Opción A (GitHub):**
  ```bash
  cd ~            # o la carpeta donde dejó el código en EA2
  git clone https://github.com/Vicenteanashei/pedidos360-backend.git pedidos360-backend-ea3   # primera vez
  # o, si ya existe la carpeta de EA2:  cd pedidos360-backend && git pull
  ```
- **Opción B (paquete):** repita exactamente el método que usó en EA2 para subir el archivo comprimido de este host y extráigalo (`tar -xzf ...`).

### Paso 6. Backup antes de tocar nada (5 min)
```bash
cd pedidos360-backend-ea3          # la carpeta del Paso 5
bash scripts/backup-host.sh backend ~/backup-ea3 infra/.env
ls -lh ~/backup-ea3
```
Debe quedar un archivo por volumen. No continúe sin backup.

### Paso 7. Agregar las claves nuevas al `.env` del backend (5 min)
El `.env` actual de EA2 (con Oracle, Rabbit y Entra) **se conserva tal cual**. Solo se **agregan** estas líneas al final:

```
KAFKA_BOOTSTRAP_SERVERS='<IP_PRIVADA_KAFKA>:9092,<IP_PRIVADA_KAFKA>:9093,<IP_PRIVADA_KAFKA>:9094'
REPORT_DB_PASSWORD='<valor de backend-ea3-agregar.env>'
AUDIT_DB_PASSWORD='<valor de backend-ea3-agregar.env>'
KAFKA_UI_USER='kafkaadmin'
KAFKA_UI_PASSWORD='<la clave de kafka-ui-aws-credenciales.txt>'
REPORT_TIME_ZONE='America/Santiago'
APP_MEMORY_LIMIT='640m'
```
- [ ] Abra `infra/.env` con `nano infra/.env` y pegue las líneas. Compruebe que existen `ENTRA_TENANT_ID`, `API_CLIENT_ID`, `ORACLE_ADMIN_PASSWORD`, `RABBITMQ_PASSWORD` y `INTERNAL_SERVICE_KEY` (de EA2). **No las cambie.**
- [ ] `REPORT_DB_PASSWORD` y `AUDIT_DB_PASSWORD` deben ser exactamente 32 caracteres hexadecimales (`0-9a-f`).

### Paso 8. Memoria y despliegue (15 min)
El backend pasa de 5 a 8 servicios más Oracle en 8 GB de RAM. Para evitar quedarse sin memoria:

```bash
free -m                              # anotar la memoria libre
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
free -m                              # debe aparecer 2 GB de Swap
```
Luego desplegar (conserva el volumen de Oracle y crea solo los usuarios `P360_REPORT` y `P360_AUDIT` si faltan):
```bash
bash scripts/deploy-host.sh backend infra/.env
```
- [ ] El script valida Rabbit, inicia Oracle, ejecuta `02-stream-users.sh`, construye las imágenes y levanta los servicios. La primera construcción puede tardar 10 minutos.
- [ ] `docker compose -p pedidos360-ea2-backend -f infra/backend/compose.yml --env-file infra/.env ps -a` debe mostrar **9 contenedores** (`oracle`, `bff`, `orders`, `catalog`, `notify`, `rabbit-admin`, `report`, `audit`, `kafka-admin`) en `Up`/`healthy`.
- [ ] `docker stats --no-stream` y `free -m`: la memoria libre no debe bajar de ~300 MB. Si baja, subir el swap o bajar `APP_MEMORY_LIMIT` a `512m` y repetir `up -d`.

Si el script falla con "políticas Rabbit": el Rabbit debe estar encendido y con sus 3 políticas DLX (ver `docs/MIGRACION-POLITICAS-RABBIT.md`).

### Paso 9. Revisar que Orders y los consumidores conectan a Kafka (3 min)
```bash
docker logs --tail 30 pedidos360-ea2-backend-orders-1   | grep -i -E "kafka|outbox|ERROR"
docker logs --tail 30 pedidos360-ea2-backend-report-1   | grep -i -E "assigned|ERROR"
docker logs --tail 30 pedidos360-ea2-backend-audit-1    | grep -i -E "assigned|ERROR"
```
- [ ] Report y Audit muestran `partitions assigned`. Si dicen `Connection ... could not be established`, el SG de Kafka no admite al backend o la IP del `.env` es incorrecta.

---

## Parte 4. Actualizar el frontend (opcional si no cambió)

### Paso 10. Reconstruir el frontend (10 min)
- [ ] Entre al host frontend por EC2 Instance Connect, obtenga el código nuevo (Paso 5) y ejecute `bash scripts/deploy-host.sh frontend infra/.env`.
- [ ] Abra https://<ID>.execute-api.us-east-1.amazonaws.com y entre con una cuenta de Entra con rol **Admin**.
- [ ] Debe aparecer la pestaña **Kafka** en el menú.

---

## Parte 5. Pruebas con login real (lo que se evalúa)

Use una cuenta **Admin** para todo, y una **Auditor** y una **Cliente** para el punto 14.

### Paso 11. Kafka UI por túnel (5 min)
Kafka UI escucha solo en `127.0.0.1` de la EC2. Para verla necesita un túnel SSH, que requiere la llave de EA2 (`vockey.pem` del Learner Lab). Si no quiere usar SSH, deje esta comprobación para la pestaña **Kafka** de la aplicación (Kafka Admin), que muestra lo mismo desde el navegador.
```bash
ssh -i vockey.pem -L 8088:127.0.0.1:8088 ec2-user@<IP pública de la EC2 Kafka>
```
Para que el SSH funcione, el SG de Kafka permite el puerto 22 solo desde la IP que usted indicó en el parámetro `SshSourceCidr` (su IP pública con `/32`). Si su IP pública cambió, edítela en EC2 → Security Groups → `<SG de Kafka>` → Inbound rules. Luego abra http://127.0.0.1:8088 (usuario `kafkaadmin`).

### Paso 12. Recorrido de un pedido (5 min)
- [ ] Crear un pedido (como Cliente u Operador) y llevarlo por los estados: aceptado → en preparación → despachado → entregado.
- [ ] Pantalla **Reportes** (Admin): el pedido entregado aparece en los KPIs. La vista se refresca cada 15 s; hay un indicador con la fecha del último evento.
- [ ] Pantalla **Auditoría** (Admin/Auditor): se ven los cinco eventos del pedido con su estado anterior y nuevo.
- [ ] Pantalla **Kafka** (Admin): tópicos `orders.events` y `audit.timeline` con 3 particiones y RF=3, y los grupos `p360-report`, `p360-audit-ingest`, `p360-audit-timeline` con lag 0.
- [ ] **Captura de pantalla de cada una** (son sus evidencias).

### Paso 13. Resiliencia (10 min)
En el host backend:
```bash
# Consumidor caído
docker stop pedidos360-ea2-backend-report-1
# crear 3 pedidos en la app y consultar Kafka UI/pantalla Kafka: lag de p360-report sube a 3
docker start pedidos360-ea2-backend-report-1
# esperar 30 s: lag vuelve a 0 y los KPIs incluyen los pedidos nuevos
```
- [ ] Mensaje inválido a la DLT: desde la pestaña Kafka (o Kafka UI) producir un mensaje inválido en `orders.events` y comprobar que aparece en `orders.events.report.DLT` y `orders.events.audit.DLT`, y que el siguiente pedido normal se procesa.
- [ ] Reparto entre consumidores: con `KAFKA_CONCURRENCY=1` en el `.env`, levantar una segunda instancia de report (`docker compose ... up -d --scale report=2` solo si el puerto no está publicado) y mostrar el reparto en la pestaña Kafka. Si se complica, muestre la evidencia local (`revision-ea3/EVIDENCIAS-LOCALES.md`: 1, 2 y 3 instancias).

### Paso 14. Roles y seguridad (3 min)
| Cuenta | Auditoría | Reportes | Kafka |
| --- | --- | --- | --- |
| Admin | sí | sí | sí |
| Auditor | sí | no (403) | no (403) |
| Cliente | no (403) | no (403) | no (403) |

### Paso 15. Administración de tópicos (5 min)
En la pestaña **Kafka** (Admin):
- [ ] Crear `demo.prueba` (3 particiones, RF=3), modificar su retención y borrarlo.
- [ ] Intentar borrar `orders.events`: debe rechazarse (403).

### Paso 16. No regresión de EA2 (5 min)
- [ ] Notificaciones por Rabbit siguen llegando; stock se reserva al aceptar y se devuelve al cancelar (la devolución tarda hasta 5 s); la pantalla Rabbit funciona.

### Paso 17. Carga (opcional, 5 min)
`scripts/stream-load.mjs` necesita un token de acceso. Sáquelo desde el navegador: F12 → Network → cualquier llamada a la API → cabecera `Authorization` (copie solo lo que va después de `Bearer`). Ejecútelo en su PC contra la URL pública:
```bash
export P360_API_URL=https://<ID>.execute-api.us-east-1.amazonaws.com P360_ACCESS_TOKEN=<token> P360_PRODUCT_ID=<id de un producto con stock> P360_STORE_ID=1 P360_LOAD_COUNT=50
node scripts/stream-load.mjs
```
El token dura ~1 hora y es una credencial: no lo guarde ni lo comparta. Anote latencia media/P95 y que el lag vuelve a 0.

---

## Parte 6. Presentación

- Guion de 10–12 minutos: `docs/GUION-EA3.md`.
- Evidencias reales para adjuntar: `revision-ea3/EVIDENCIAS-AWS.md` (despliegue en AWS), `EVIDENCIAS-LOCALES.md` (pruebas de resiliencia, rebalanceo y carga) y sus capturas del Paso 12.
- Aclaración que conviene decir: tres brokers en una sola EC2 cumplen la topología lógica del laboratorio, pero no sobreviven a la pérdida de esa EC2.
- React se mantiene por autorización del docente y Oracle sigue en AWS.

## Parte 7. Después de presentar

- [ ] Detener las instancias: Instance state → **Stop instance** en las cuatro.
- [ ] Si ya no las necesita: CloudFormation → `pedidos360-ea3-kafka` → **Delete stack**.
- [ ] **End Lab** en el Learner Lab.

## Si algo falla

| Síntoma | Qué hacer |
| --- | --- |
| Kafka no responde tras iniciar la EC2 | Esperar 3 min; reiniciar la instancia; revisar `Get system log`. |
| Report/Audit no conectan a Kafka | SG de Kafka debe admitir 9092–9094 desde el SG del backend; revisar `KAFKA_BOOTSTRAP_SERVERS`. |
| `deploy-host.sh` falla por Rabbit | Encender Rabbit; ver `docs/MIGRACION-POLITICAS-RABBIT.md`. |
| El backend se queda sin memoria | Agregar swap; bajar `APP_MEMORY_LIMIT` a `512m`; `docker stats`. |
| Reportes vacíos tras migrar | Un Admin puede llamar `POST /api/outbox/stream/replay?page=0&size=100` (y `page=1`, ... mientras `hasNext` sea true) para proyectar los pedidos existentes. |
| 401 en todas las rutas | Token vencido o `ENTRA_*`/`API_CLIENT_ID` incorrectos en el `.env`. |
| Saldo del laboratorio bajo | Detener instancias; eliminar el balanceador si no se necesita. |
