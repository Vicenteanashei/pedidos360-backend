// Genera infra/aws/kafka-host-auto-lab.json a partir de kafka-host-lab.json.
// La instancia se configura sola al arrancar (sin SSH): instala Docker, escribe
// infra/kafka/compose.yml e init-topics.sh, genera kafka.env con su IP privada y levanta el clúster.
// Uso: node scripts/build-kafka-auto-template.mjs
import {readFileSync, writeFileSync} from 'node:fs';
import {gzipSync} from 'node:zlib';

const read = p => readFileSync(new URL('../' + p, import.meta.url));
const pack = p => gzipSync(read(p), {level: 9}).toString('base64');

const template = JSON.parse(read('infra/aws/kafka-host-lab.json').toString('utf8'));
template.Description =
  'Pedidos360 EA3: host Kafka autoconfigurable (3 ZooKeeper + 3 brokers + Kafka UI en una EC2). ' +
  'Sin SSH ni llaves: el arranque despliega el cluster. Sin roles IAM.';
template.Parameters.KafkaUiPassword = {
  Type: 'String',
  NoEcho: true,
  MinLength: 16,
  MaxLength: 64,
  AllowedPattern: '^[A-Za-z0-9]+$',
  Description: 'Clave de Kafka UI (usuario kafkaadmin). Solo letras y numeros, 16 a 64 caracteres.'
};
template.Parameters.SshSourceCidr.Description =
  'CIDR IPv4 administrativo (opcional: el despliegue no usa SSH); nunca 0.0.0.0/0';

const script = [
  '#!/bin/bash',
  'set -euo pipefail',
  '# La consola serie es lentisima: solo las lineas [p360] van a ella; el detalle va a un archivo.',
  'exec 3> >(logger -t p360-kafka -s 2>/dev/console)',
  'say() { echo "[p360] $*" >&3; echo "[p360] $*"; }',
  'exec >> /var/log/pedidos360-kafka-boot.log 2>&1',
  'say "inicio $(date -u +%FT%TZ)"',
  'dnf install -y -q docker',
  'say "docker instalado"',
  'systemctl enable --now docker',
  'mkdir -p /usr/local/lib/docker/cli-plugins',
  'curl -fsSL https://github.com/docker/compose/releases/download/v2.40.3/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose',
  'chmod +x /usr/local/lib/docker/cli-plugins/docker-compose',
  'mkdir -p /opt/pedidos360/kafka',
  'cd /opt/pedidos360/kafka',
  `echo '${pack('infra/kafka/compose.yml')}' | base64 -d | gunzip > compose.yml`,
  `echo '${pack('infra/kafka/init-topics.sh')}' | base64 -d | gunzip > init-topics.sh`,
  'TOKEN=$(curl -fsSX PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 300")',
  'IP=$(curl -fs -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/local-ipv4)',
  'say "IP privada $IP"',
  'umask 077',
  'cat > kafka.env <<EOF',
  'KAFKA_ADVERTISED_HOST=$IP',
  'KAFKA_BIND_ADDRESS=$IP',
  'KAFKA_UI_BIND_ADDRESS=127.0.0.1',
  'KAFKA_UI_USER=kafkaadmin',
  'KAFKA_UI_PASSWORD=${KafkaUiPassword}',
  'EOF',
  'say "descargando imagenes y levantando el cluster"',
  'docker compose --env-file kafka.env -p pedidos360-ea3-kafka -f compose.yml up -d --quiet-pull || say "compose up termino con error (ver /var/log/pedidos360-kafka-boot.log)"',
  'state=none',
  'for i in $(seq 1 90); do',
  '  state=$(docker inspect -f "{{.State.Status}} {{.State.ExitCode}}" pedidos360-ea3-kafka-kafka-init-1 2>/dev/null || echo none)',
  '  [ "$state" = "exited 0" ] && break',
  '  sleep 10',
  'done',
  'say "kafka-init: $state"',
  'docker ps --format "{{.Names}} {{.Status}}" | while IFS= read -r l; do say "$l"; done || true',
  'docker exec pedidos360-ea3-kafka-kafka1-1 kafka-topics --bootstrap-server localhost:19092 --describe | grep "^Topic:" | while IFS= read -r l; do say "$l"; done || true',
  'say "LISTO $(date -u +%FT%TZ)"',
  ''
].join('\n');

template.Resources.KafkaHost.Properties.UserData = {'Fn::Base64': {'Fn::Sub': script}};
template.Outputs.KafkaBootstrapServers = {
  Value: {'Fn::Sub': '${KafkaHost.PrivateIp}:9092,${KafkaHost.PrivateIp}:9093,${KafkaHost.PrivateIp}:9094'}
};

const out = new URL('../infra/aws/kafka-host-auto-lab.json', import.meta.url);
writeFileSync(out, JSON.stringify(template, null, 2) + '\n');
console.log('Generado', out.pathname, '- UserData', Buffer.byteLength(script), 'bytes (límite 16384)');
