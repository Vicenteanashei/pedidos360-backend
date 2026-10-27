#!/usr/bin/env python3
"""Inspect by default; migrate only empty, unused application queues with publishers stopped.

Never reads/purges message bodies, deletes a DLQ, changes credentials, or starts/stops services.
Run while all five application services are stopped, then start the newly built version.
"""
from pathlib import Path
from urllib import request, error, parse
import argparse
import base64
import datetime as dt
import ipaddress
import json
import os
import re
import shlex
import time

ROOT = Path(__file__).resolve().parents[1]
VHOST = '/pedidos360'
QUEUES = ['q.cmd.email', 'q.cmd.kitchen', 'q.cmd.invoice']
SOURCES = {'cmd.direct', 'cmd.topic', 'cmd.dead.dlx'}
TOPICS = ['email.#', 'kitchen.#', 'invoice.*']


def read_env(path):
    values = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        value = value.strip()
        values[key.strip()] = shlex.split(value)[0] if value.startswith(('"', "'")) else value
    return values


class Broker:
    def __init__(self, url, user, password):
        target = parse.urlsplit(url)
        if target.scheme not in {'http', 'https'} or not target.hostname or target.username or target.password or target.query or target.fragment or target.path not in {'', '/'}:
            raise ValueError('URL Management inválida; usar un origen privado sin credenciales.')
        if target.hostname != 'localhost':
            address = ipaddress.ip_address(target.hostname)
            private = address.is_loopback or any(address in ipaddress.ip_network(n) for n in ['10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'])
            if not private:
                raise ValueError('Management debe estar en localhost o una IP privada.')
        self.url = url.rstrip('/') + '/api'
        self.auth = 'Basic ' + base64.b64encode((user + ':' + password).encode()).decode()

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        req = request.Request(self.url + path, data=data, method=method,
                              headers={'Authorization': self.auth, 'Content-Type': 'application/json'})
        try:
            with request.urlopen(req, timeout=15) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except error.HTTPError as exc:
            # Do not print the broker response, authentication, message content or traceback.
            raise RuntimeError(f'Rabbit HTTP {exc.code} en {method} {path}') from None
        except error.URLError:
            raise RuntimeError('Management no disponible; no se muestran credenciales.') from None


class PolicyMigration:
    def __init__(self, broker, app_user, definitions):
        self.broker, self.app_user = broker, app_user
        self.v = parse.quote(VHOST, safe='')
        self.policies = definitions['policies']
        if len(self.policies) != 3 or {p['vhost'] for p in self.policies} != {VHOST}:
            raise ValueError('Se requieren exactamente las tres políticas del vhost del proyecto.')
        self.bindings = []
        for queue, topic in zip(QUEUES, TOPICS):
            policy = [p for p in self.policies if re.fullmatch(p['pattern'], queue)]
            if len(policy) != 1 or re.fullmatch(policy[0]['pattern'], queue + '.dlq'):
                raise ValueError('La política debe seleccionar solo su cola principal.')
            definition = policy[0]['definition']
            if definition['dead-letter-exchange'] != 'cmd.dead.dlx':
                raise ValueError('DLX inesperado.')
            key = definition['dead-letter-routing-key']
            for source, destination, routing in [('cmd.direct', queue, key), ('cmd.topic', queue, topic), ('cmd.dead.dlx', queue + '.dlq', key)]:
                self.bindings.append({'source': source, 'destination': destination, 'routing_key': routing, 'arguments': {}})

    def call(self, method, path, body=None):
        return self.broker.call(method, path, body)

    def snapshot(self):
        targets = set(QUEUES + [q + '.dlq' for q in QUEUES])
        rows = self.call('GET', '/queues/' + self.v)
        queues = {q['name']: {k: q.get(k) for k in ['name', 'durable', 'auto_delete', 'arguments', 'messages_ready', 'messages_unacknowledged', 'consumers', 'policy', 'effective_policy_definition']} for q in rows if q['name'] in targets}
        bindings = [b for b in self.call('GET', '/bindings/' + self.v) if b['source'] in SOURCES and b['destination'] in targets and b.get('destination_type', 'queue') == 'queue']
        policies = self.call('GET', '/policies/' + self.v)
        nodes = self.call('GET', '/nodes')
        connected = any(c.get('vhost') == VHOST for c in self.call('GET', '/connections'))
        blockers = []
        if set(queues) != targets:
            blockers.append('Faltan colas del proyecto; no se aplica una migración parcial.')
        if len([n for n in nodes if n.get('running')]) != 3 or any(n.get('partitions') for n in nodes):
            blockers.append('Se requieren los tres nodos activos y sin particiones.')
        if connected:
            blockers.append('Hay conexiones AMQP al vhost; detener aplicaciones y cualquier publicador externo.')
        allowed = {self.binding_key(b) for b in self.bindings}
        for queue in QUEUES:
            allowed.add(self.binding_key({'source': 'cmd.dead.dlx', 'destination': queue + '.dlq', 'routing_key': queue + '.dlq'}))
        allowed.add(self.binding_key({'source': 'cmd.topic', 'destination': 'q.cmd.invoice', 'routing_key': 'invoice.#'}))
        if any(self.binding_key(b) not in allowed for b in bindings):
            blockers.append('Hay bindings personalizados en la topología; revisar antes de reemplazarlos.')
        for queue in QUEUES:
            q = queues.get(queue, {})
            if any(q.get(k, 0) for k in ['messages_ready', 'messages_unacknowledged', 'consumers']):
                blockers.append(queue + ': requiere Ready=0, Unacked=0 y Consumers=0.')
            if q and (not q['durable'] or q['auto_delete'] or q['arguments'].get('x-queue-type', 'classic') != 'classic'):
                blockers.append(queue + ': tipo o durabilidad inesperados.')
            if q.get('policy') and q['policy'] not in {p['name'] for p in self.policies}:
                blockers.append(queue + ': existe otra política activa; requiere revisión.')
        return {'queues': queues, 'bindings': bindings, 'policies': policies, 'blockers': blockers,
                'clusterReady': len([n for n in nodes if n.get('running')]) == 3 and not any(n.get('partitions') for n in nodes)}

    def configuration_blockers(self, state):
        """Deployment check permits live consumers or a fresh install; never mutates the broker."""
        issues = []
        if not state['clusterReady']:
            issues.append('Se requieren tres nodos activos sin particiones.')
        existing = {p['name']: p for p in state['policies']}
        for expected in self.policies:
            actual = existing.get(expected['name'], {})
            if any(actual.get(k) != expected[k] for k in ['pattern', 'apply-to', 'priority', 'definition']):
                issues.append('Política ausente o distinta: ' + expected['name'])
        if not state['queues']:
            return issues
        if len(state['queues']) != 6:
            issues.append('Topología incompleta; revisar antes de arrancar backend.')
        for queue in QUEUES:
            row = state['queues'].get(queue, {})
            if any(k.startswith('x-dead-letter-') for k in row.get('arguments', {})):
                issues.append(queue + ': aplicar migración de argumentos antes de desplegar.')
        if {self.binding_key(b) for b in state['bindings']} != {self.binding_key(b) for b in self.bindings}:
            issues.append('Bindings no alineados; aplicar migración antes de desplegar.')
        return issues

    def queue_path(self, queue):
        return '/queues/' + self.v + '/' + parse.quote(queue, safe='')

    def binding_key(self, binding):
        return tuple(binding[k] for k in ['source', 'destination', 'routing_key']) + (json.dumps(binding.get('arguments', {}), sort_keys=True),)

    def sync_bindings(self, desired):
        current = self.snapshot_bindings()
        for b in desired:
            if self.binding_key(b) not in {self.binding_key(x) for x in current}:
                self.call('POST', self.binding_path(b), {'routing_key': b['routing_key'], 'arguments': b.get('arguments', {})})
        wanted = {self.binding_key(b) for b in desired}
        for b in current:
            if self.binding_key(b) not in wanted:
                self.call('DELETE', self.binding_path(b) + '/' + parse.quote(b['properties_key'], safe=''))

    def binding_path(self, binding):
        return '/bindings/' + self.v + '/e/' + parse.quote(binding['source'], safe='') + '/q/' + parse.quote(binding['destination'], safe='')

    def snapshot_bindings(self):
        targets = set(QUEUES + [q + '.dlq' for q in QUEUES])
        return [b for b in self.call('GET', '/bindings/' + self.v) if b['source'] in SOURCES and b['destination'] in targets and b.get('destination_type', 'queue') == 'queue']

    def put_queue(self, queue, arguments, snapshot):
        self.call('PUT', self.queue_path(queue), {'durable': snapshot['durable'], 'auto_delete': snapshot['auto_delete'], 'arguments': arguments})

    def put_policy(self, policy):
        self.call('PUT', '/policies/' + self.v + '/' + parse.quote(policy['name'], safe=''), {k: policy[k] for k in ['pattern', 'apply-to', 'priority', 'definition']})

    def apply(self, before):
        if before['blockers']:
            raise RuntimeError('Migración bloqueada: ' + '; '.join(before['blockers']))
        # Repeat the guards immediately before mutations; HTTP conditional deletion is the final guard.
        latest = self.snapshot()
        if latest['blockers']:
            raise RuntimeError('El estado cambió; migración cancelada sin mutaciones.')
        changed = []
        try:
            for policy in self.policies:
                self.put_policy(policy)
            for queue in QUEUES:
                q = before['queues'][queue]
                args = {k: v for k, v in q['arguments'].items() if k not in {'x-dead-letter-exchange', 'x-dead-letter-routing-key'}}
                if args != q['arguments']:
                    self.call('DELETE', self.queue_path(queue) + '?if-empty=true&if-unused=true')
                    changed.append(queue)
                    self.put_queue(queue, args, q)
            self.sync_bindings(self.bindings)
            # Management queue aggregates can lag a recreation by a few sampling intervals.
            deadline = time.monotonic() + 25
            while True:
                after = self.snapshot()
                ready = all(
                    after['queues'].get(queue, {}).get('policy') == policy['name']
                    and after['queues'].get(queue, {}).get('effective_policy_definition') == policy['definition']
                    and not any(k.startswith('x-dead-letter-') for k in after['queues'].get(queue, {}).get('arguments', {}))
                    for queue in QUEUES
                    for policy in self.policies if re.fullmatch(policy['pattern'], queue)
                )
                if ready or time.monotonic() >= deadline:
                    break
                time.sleep(1)
            for queue in QUEUES:
                q = after['queues'][queue]
                policy = next(p for p in self.policies if re.fullmatch(p['pattern'], queue))
                if q['policy'] != policy['name'] or q['effective_policy_definition'] != policy['definition']:
                    raise RuntimeError('La política efectiva no coincide con la configuración esperada.')
                dlq = queue + '.dlq'
                if any(after['queues'][dlq][k] != before['queues'][dlq][k] for k in ['messages_ready', 'messages_unacknowledged']):
                    raise RuntimeError('Cambió el conteo de una DLQ; conservar el respaldo y revisar.')
            return after
        except Exception as cause:
            try:
                for queue in changed:
                    # These calls also refuse to delete if a new message/consumer appeared.
                    if any(q['name'] == queue for q in self.call('GET', '/queues/' + self.v)):
                        self.call('DELETE', self.queue_path(queue) + '?if-empty=true&if-unused=true')
                    self.put_queue(queue, before['queues'][queue]['arguments'], before['queues'][queue])
                self.sync_bindings(before['bindings'])
                previous = {p['name']: p for p in before['policies']}
                for policy in self.policies:
                    if policy['name'] in previous:
                        self.put_policy(previous[policy['name']])
                    else:
                        self.call('DELETE', '/policies/' + self.v + '/' + parse.quote(policy['name'], safe=''))
            except Exception:
                raise RuntimeError('Migración incompleta; restauración automática detenida para proteger mensajes. Mantener aplicaciones detenidas y revisar el respaldo.') from None
            detail = str(cause) if isinstance(cause, RuntimeError) else type(cause).__name__
            raise RuntimeError('Falló la migración; topología previa restaurada. Aplicaciones siguen detenidas. Detalle: ' + detail) from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=ROOT / 'infra/.env')
    parser.add_argument('--management-url')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--verify-ready', action='store_true')
    parser.add_argument('--backup-dir', type=Path, default=Path.home() / '.local/share/pedidos360/backups/rabbit-policy-migrations')
    args = parser.parse_args()
    try:
        env = read_env(args.env_file)
        api = Broker(args.management_url or env.get('RABBIT_MANAGEMENT_URL') or 'http://127.0.0.1:15672', env['RABBIT_ADMIN_USER'], env['RABBIT_ADMIN_PASSWORD'])
        migration = PolicyMigration(api, env['RABBITMQ_USER'], json.loads((ROOT / 'infra/mq/policies.json').read_text()))
        before = migration.snapshot()
        if args.verify_ready:
            issues = migration.configuration_blockers(before)
            print(json.dumps({'readyForNewBackend': not issues, 'freshInstall': not before['queues'], 'issues': issues}, ensure_ascii=False))
            return int(bool(issues))
        result = {'applied': False, 'blockers': before['blockers'], 'mainQueues': {q: {k: v for k, v in row.items() if k in ['messages_ready', 'messages_unacknowledged', 'consumers']} for q, row in before['queues'].items() if q in QUEUES}}
        if args.apply:
            if before['blockers']:
                raise RuntimeError('Migración bloqueada: ' + '; '.join(before['blockers']))
            backup = args.backup_dir.resolve()
            if any((parent / '.git').exists() for parent in [backup, *backup.parents]):
                raise ValueError('Guardar el respaldo fuera de repositorios Git.')
            backup.mkdir(parents=True, exist_ok=True, mode=0o700)
            os.chmod(backup, 0o700)
            stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
            dest = backup / (stamp + '-topologia-previa.json')
            fd = os.open(dest, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'w') as file:
                json.dump(before, file, indent=2)
            after = migration.apply(before)
            result.update(applied=True, backup=str(dest), policies=[p['name'] for p in migration.policies], dlqMessagesPreserved=sum(after['queues'][q + '.dlq']['messages_ready'] for q in QUEUES))
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (RuntimeError, ValueError, KeyError, OSError) as exc:
        # Only controlled exceptions are printable. OS/credential parse details are not needed.
        message = str(exc) if isinstance(exc, (RuntimeError, ValueError)) else 'No se pudo leer configuración o completar el acceso; valores privados omitidos.'
        print(json.dumps({'applied': False, 'error': message}, ensure_ascii=False))
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
