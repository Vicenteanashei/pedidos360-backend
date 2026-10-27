#!/usr/bin/env python3
"""Manual RabbitMQ lab commands. Reads private credentials; never prints them.

Publishes only synthetic test envelopes to the three existing task exchanges/keys.
Peek always requeues. No purge, delete, credential change or topology mutation.
"""
import argparse
import base64
import datetime as dt
import ipaddress
import json
from pathlib import Path
import shlex
import sys
from urllib import error, parse, request
import uuid

ROOT = Path(__file__).resolve().parents[1]
VHOST = '/pedidos360'
TASKS = {'email': ('email.send', 'CREADO'),
         'kitchen': ('kitchen.ticket', 'ACEPTADO'),
         'invoice': ('invoice.gen', 'ENTREGADO')}
QUEUES = {f'q.cmd.{task}{suffix}' for task in TASKS for suffix in ['', '.dlq']}


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError('Management redirige; revisar URL privada. No se siguen redirecciones.')


def read_env(path):
    values = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        words = shlex.split(value)
        values[key.strip()] = words[0] if words else ''
    return values


class Broker:
    def __init__(self, url, user, password):
        target = parse.urlsplit(url)
        if (target.scheme not in {'http', 'https'} or not target.hostname
                or target.username or target.password or target.query or target.fragment
                or target.path not in {'', '/'}):
            raise ValueError('Usar un origen Management privado sin credenciales en la URL.')
        if target.hostname != 'localhost':
            address = ipaddress.ip_address(target.hostname)
            if not (address.is_loopback or any(address in ipaddress.ip_network(net)
                    for net in ['10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'])):
                raise ValueError('Management debe estar en localhost o una IP privada.')
        self.url = url.rstrip('/') + '/api'
        self.auth = 'Basic ' + base64.b64encode((user + ':' + password).encode()).decode()
        self.opener = request.build_opener(request.ProxyHandler({}), NoRedirect())

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        req = request.Request(self.url + path, method=method, data=data,
                              headers={'Authorization': self.auth, 'Content-Type': 'application/json'})
        try:
            with self.opener.open(req, timeout=15) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except error.HTTPError as exc:
            raise RuntimeError(f'Rabbit Management HTTP {exc.code}; revisar servicio/permisos.') from None
        except error.URLError:
            raise RuntimeError('Management no disponible por red privada.') from None


def state(broker):
    v = parse.quote(VHOST, safe='')
    nodes = broker.call('GET', '/nodes')
    queues = broker.call('GET', '/queues/' + v)
    policies = broker.call('GET', '/policies/' + v)
    bindings = broker.call('GET', '/bindings/' + v)
    return {'vhost': VHOST,
            'nodes': [{k: row.get(k) for k in ['name', 'running', 'partitions', 'mem_alarm', 'disk_free_alarm']}
                      for row in nodes],
            'queues': [{k: row.get(k) for k in ['name', 'node', 'state', 'durable', 'auto_delete',
                       'messages_ready', 'messages_unacknowledged', 'consumers', 'policy',
                       'effective_policy_definition']}
                       for row in queues if row['name'] in QUEUES],
            'policies': [{k: row.get(k) for k in ['name', 'pattern', 'definition']}
                         for row in policies if row['name'].startswith('policy.')],
            'bindings': [{k: row.get(k) for k in ['source', 'destination', 'routing_key']}
                         for row in bindings if row['source'] in {'cmd.direct', 'cmd.topic', 'cmd.dead.dlx'}
                         and row['destination'] in QUEUES]}


def envelope(task, event_id=None, failure='none'):
    key, status = TASKS[task]
    identifier = str(uuid.UUID(event_id)) if event_id else str(uuid.uuid4())
    event = {'eventId': identifier, 'type': key,
             'timestamp': dt.datetime.now(dt.timezone.utc).isoformat(),
             'correlationId': identifier, 'ownerId': 'prueba-rabbit:cliente-ficticio',
             'payload': {'id': 900001, 'storeId': 1, 'customerName': 'Prueba académica Rabbit',
                         'customerEmail': 'cliente@example.test', 'deliveryAddress': 'Dirección ficticia',
                         'notes': 'Mensaje sintético de prueba Rabbit; no es una compra real',
                         'status': status, 'total': 2200,
                         'items': [{'productId': 1, 'quantity': 1, 'unitPrice': 2200, 'subtotal': 2200}]}}
    if failure == 'invalid':
        event['type'] = 'prueba.incompatible'
    elif failure == 'retry':
        event['demoFailure'] = True
    return event


def publish(broker, args):
    event = envelope(args.task, args.event_id, args.failure)
    key = args.routing_key or TASKS[args.task][0]
    if not key.startswith(args.task + '.') or len(key) > 120 or not all(c.isalnum() or c in '._-' for c in key):
        raise ValueError('La clave debe pertenecer a la tarea elegida y no contener comodines.')
    exchange = 'cmd.' + args.exchange
    result = broker.call('POST', '/exchanges/' + parse.quote(VHOST, safe='') + '/' + exchange + '/publish',
                         {'properties': {'delivery_mode': 2, 'content_type': 'application/json',
                                         'message_id': event['eventId'], 'correlation_id': event['correlationId']},
                          'routing_key': key, 'payload': json.dumps(event), 'payload_encoding': 'string'})
    return {'eventId': event['eventId'], 'exchange': exchange, 'routingKey': key,
            'deliveryMode': 2, 'failure': args.failure, 'routed': result.get('routed'),
            'meaning': 'Enrutamiento al broker; no equivale al ACK del consumidor.'}


def peek(broker, queue):
    rows = broker.call('POST', '/queues/' + parse.quote(VHOST, safe='') + '/' + parse.quote(queue, safe='') + '/get',
                      {'count': 1, 'ackmode': 'ack_requeue_true', 'encoding': 'auto', 'truncate': 100000})
    result = []
    for row in rows:
        try:
            payload = row.get('payload', '')
            if row.get('payload_encoding') == 'base64':
                payload = base64.b64decode(payload)
            event = json.loads(payload)
        except (ValueError, TypeError):
            event = {}
        properties = row.get('properties', {})
        death = properties.get('headers', {}).get('x-death', [])
        result.append({'eventId': event.get('eventId') if isinstance(event, dict) else None,
                       'type': event.get('type') if isinstance(event, dict) else None,
                       'deliveryMode': properties.get('delivery_mode'),
                       'redelivered': row.get('redelivered'), 'requeued': True,
                       'xDeath': [{k: d.get(k) for k in ['count', 'reason', 'queue', 'exchange', 'routing-keys']}
                                  for d in death]})
    return {'queue': queue, 'messages': result,
            'note': 'Se lee como máximo un mensaje y se reencola; puede cambiar redelivered y el orden.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=ROOT / 'infra/.env')
    parser.add_argument('--management-url')
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('state', help='Leer nodos, colas, políticas y bindings.')
    send = commands.add_parser('publish', help='Publicar UN mensaje sintético persistente.')
    send.add_argument('--task', choices=TASKS, required=True)
    send.add_argument('--exchange', choices=['direct', 'topic'], default='direct')
    send.add_argument('--routing-key')
    send.add_argument('--event-id', help='Reusar UUID solo para la prueba de idempotencia.')
    send.add_argument('--failure', choices=['none', 'invalid', 'retry'], default='none')
    inspect = commands.add_parser('peek', help='Inspeccionar un mensaje y reencolarlo.')
    inspect.add_argument('--queue', choices=sorted(QUEUES), required=True)
    args = parser.parse_args()
    try:
        env = read_env(args.env_file)
        broker = Broker(args.management_url or env.get('RABBIT_MANAGEMENT_URL', 'http://127.0.0.1:15672'),
                        env['RABBIT_ADMIN_USER'], env['RABBIT_ADMIN_PASSWORD'])
        result = state(broker) if args.command == 'state' else publish(broker, args) if args.command == 'publish' else peek(broker, args.queue)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, KeyError, RuntimeError) as exc:
        # Do not show config content, credentials, HTTP bodies or exception messages from parsers.
        print(json.dumps({'ok': False, 'errorType': type(exc).__name__,
                          'hint': 'Revisar .env privado, Management privado y argumentos; usar --help.'}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
