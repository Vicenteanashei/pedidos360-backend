"""Guards and rollback for the migration; no broker credentials or Docker required."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
from urllib.parse import unquote
import json
import unittest
from unittest.mock import patch

spec = spec_from_file_location('migration', Path(__file__).with_name('migrate-rabbit-policies.py'))
m = module_from_spec(spec)
spec.loader.exec_module(m)
DEFINITIONS = json.loads((m.ROOT / 'infra/mq/policies.json').read_text())


class FakeBroker:
    def __init__(self):
        self.queues = {}
        self.bindings = []
        self.policies = []
        self.connections = []
        self.mutations = []
        self.fail_creation = False
        for queue, key, topic in zip(m.QUEUES, ['email.send', 'kitchen.ticket', 'invoice.gen'], ['email.#', 'kitchen.#', 'invoice.#']):
            for name in [queue, queue + '.dlq']:
                self.queues[name] = dict(name=name, durable=True, auto_delete=False, arguments={}, messages_ready=0 if name == queue else 7, messages_unacknowledged=0, consumers=0, policy=None, effective_policy_definition={})
            self.queues[queue]['arguments'] = {'x-dead-letter-exchange': 'cmd.dead.dlx', 'x-dead-letter-routing-key': queue + '.dlq'}
            for source, dest, routing in [('cmd.direct', queue, key), ('cmd.topic', queue, topic), ('cmd.dead.dlx', queue + '.dlq', queue + '.dlq')]:
                self.bindings.append(dict(source=source, destination=dest, routing_key=routing, arguments={}, properties_key=routing))

    def call(self, method, path, body=None):
        parts = [unquote(p) for p in path.split('?')[0].strip('/').split('/')]
        if method == 'GET':
            if parts[0] == 'queues':
                rows = deepcopy(list(self.queues.values()))
                for row in rows:
                    match = next((p for p in self.policies if m.re.fullmatch(p['pattern'], row['name'])), None)
                    row['policy'] = match['name'] if match else None
                    row['effective_policy_definition'] = match['definition'] if match else {}
                return rows
            if parts[0] == 'bindings':
                return deepcopy(self.bindings)
            if parts[0] == 'policies':
                return deepcopy(self.policies)
            if parts[0] == 'nodes':
                return [dict(running=True, partitions=[]) for _ in range(3)]
            if parts[0] == 'connections':
                return deepcopy(self.connections)
        self.mutations.append((method, path))
        if parts[0] == 'policies':
            self.policies = [p for p in self.policies if p['name'] != parts[2]]
            if method == 'PUT':
                self.policies.append(dict(vhost=m.VHOST, name=parts[2], **body))
        elif parts[0] == 'queues':
            name = parts[2]
            if method == 'DELETE':
                assert name in m.QUEUES, 'Never delete a DLQ'
                assert '?if-empty=true&if-unused=true' in path
                q = self.queues[name]
                if any(q[k] for k in ['messages_ready', 'messages_unacknowledged', 'consumers']):
                    raise RuntimeError('Conditional deletion refused')
                del self.queues[name]
                self.bindings = [b for b in self.bindings if b['destination'] != name]
            elif method == 'PUT':
                if self.fail_creation:
                    self.fail_creation = False
                    raise RuntimeError('Injected failure after deleting an empty queue')
                self.queues[name] = dict(name=name, messages_ready=0, messages_unacknowledged=0, consumers=0, policy=None, effective_policy_definition={}, **body)
        elif parts[0] == 'bindings':
            source, destination = parts[3], parts[5]
            if method == 'POST':
                self.bindings.append(dict(source=source, destination=destination, properties_key=body['routing_key'], **body))
            else:
                self.bindings = [b for b in self.bindings if not (b['source'] == source and b['destination'] == destination and b['properties_key'] == parts[6])]
        else:
            raise AssertionError((method, path))


class MigrationTest(unittest.TestCase):
    def setUp(self):
        self.api = FakeBroker()
        self.migration = m.PolicyMigration(self.api, 'app', DEFINITIONS)

    def test_pending_unacked_or_consumers_refuse_before_any_mutation(self):
        for field in ['messages_ready', 'messages_unacknowledged', 'consumers']:
            with self.subTest(field=field):
                self.api.queues[m.QUEUES[0]][field] = 1
                with self.assertRaises(RuntimeError):
                    self.migration.apply(self.migration.snapshot())
                self.assertEqual([], self.api.mutations)
                self.api.queues[m.QUEUES[0]][field] = 0

    def test_publisher_connection_refuses_even_with_empty_queues(self):
        self.api.connections = [dict(user='app', vhost=m.VHOST)]
        with self.assertRaises(RuntimeError):
            self.migration.apply(self.migration.snapshot())
        self.assertEqual([], self.api.mutations)

    def test_custom_binding_is_not_removed(self):
        self.api.bindings.append(dict(source='cmd.topic', destination=m.QUEUES[0], routing_key='custom.#', arguments={}, properties_key='custom.#'))
        with self.assertRaises(RuntimeError):
            self.migration.apply(self.migration.snapshot())
        self.assertEqual([], self.api.mutations)

    def test_other_amqp_users_also_block_migration(self):
        self.api.connections = [dict(user='another-publisher', vhost=m.VHOST)]
        with self.assertRaises(RuntimeError):
            self.migration.apply(self.migration.snapshot())
        self.assertEqual([], self.api.mutations)

    def test_deployment_check_refuses_legacy_arguments(self):
        self.assertTrue(self.migration.configuration_blockers(self.migration.snapshot()))
        self.assertEqual([], self.api.mutations)

    def test_deployment_check_permits_live_connections_after_migration(self):
        self.migration.apply(self.migration.snapshot())
        self.api.connections = [dict(user='app', vhost=m.VHOST)]
        self.assertEqual([], self.migration.configuration_blockers(self.migration.snapshot()))

    def test_fresh_install_requires_policies_before_backend_can_start(self):
        self.api.queues = {}
        self.api.bindings = []
        self.assertTrue(self.migration.configuration_blockers(self.migration.snapshot()))
        self.api.policies = deepcopy(DEFINITIONS['policies'])
        self.assertEqual([], self.migration.configuration_blockers(self.migration.snapshot()))

    def test_migration_preserves_dlq_messages_and_can_be_repeated(self):
        before = self.migration.snapshot()
        after = self.migration.apply(before)
        self.assertEqual(3, len(self.api.policies))
        self.assertEqual(9, len(self.api.bindings))
        for queue in m.QUEUES:
            self.assertEqual(7, after['queues'][queue + '.dlq']['messages_ready'])
            self.assertEqual({}, after['queues'][queue]['arguments'])
        mutations = len(self.api.mutations)
        self.migration.apply(self.migration.snapshot())
        self.assertFalse(any(method == 'DELETE' for method, _ in self.api.mutations[mutations:]))

    def test_failure_after_deletion_restores_the_original_queue_and_topology(self):
        before = self.migration.snapshot()
        self.api.fail_creation = True
        with self.assertRaisesRegex(RuntimeError, 'topología previa restaurada'):
            self.migration.apply(before)
        after = self.migration.snapshot()
        self.assertEqual(before['queues'], after['queues'])
        self.assertEqual({self.migration.binding_key(b) for b in before['bindings']}, {self.migration.binding_key(b) for b in after['bindings']})
        self.assertEqual([], self.api.policies)

    def test_management_metadata_lag_does_not_trigger_a_false_rollback(self):
        original = self.api.call
        stale_reads = []
        def delayed(method, path, body=None):
            rows = original(method, path, body)
            if method == 'GET' and path.startswith('/queues/') and self.api.policies and all(not self.api.queues[q]['arguments'] for q in m.QUEUES) and len(stale_reads) < 2:
                stale_reads.append(True)
                for row in rows:
                    row['policy'] = None
            return rows
        self.api.call = delayed
        with patch.object(m.time, 'sleep') as sleep:
            after = self.migration.apply(self.migration.snapshot())
        self.assertEqual(2, sleep.call_count)
        self.assertTrue(all(after['queues'][q]['policy'] for q in m.QUEUES))


if __name__ == '__main__':
    unittest.main()
