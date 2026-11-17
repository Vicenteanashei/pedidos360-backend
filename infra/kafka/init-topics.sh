#!/usr/bin/env bash
set -euo pipefail
bootstrap=kafka1:19092,kafka2:19092,kafka3:19092
create() {
  local name="$1" policy="$2" retention="$3"
  kafka-topics --bootstrap-server "$bootstrap" --create --if-not-exists \
    --topic "$name" --partitions 3 --replication-factor 3 \
    --config "cleanup.policy=$policy" --config "retention.ms=$retention" --config min.insync.replicas=2
  # Existing topics are checked; this script never deletes data or silently changes replicas.
  description="$(kafka-topics --bootstrap-server "$bootstrap" --describe --topic "$name")"
  if ! grep -Eq 'PartitionCount: 3[[:space:]]+ReplicationFactor: 3' <<< "$description"; then
    echo "Topic $name must have 3 partitions and replication factor 3; reconcile explicitly." >&2
    exit 1
  fi
  kafka-configs --bootstrap-server "$bootstrap" --alter --entity-type topics --entity-name "$name" \
    --add-config "cleanup.policy=[$policy],retention.ms=$retention,min.insync.replicas=2"
}
create orders.events delete 604800000
# Key = eventId, preserving the full history under compaction.
create audit.timeline 'compact,delete' 2592000000
for topic in orders.events.report.DLT orders.events.audit.DLT audit.timeline.timeline.DLT; do
  create "$topic" delete 1209600000
done
echo 'Kafka topology checked: two business topics and three consumer-specific DLTs.'

