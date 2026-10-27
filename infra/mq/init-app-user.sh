#!/bin/bash
set -euo pipefail
[[ "$RABBITMQ_USER" =~ ^[A-Za-z0-9_-]+$ ]] || exit 1
users=$(rabbitmqctl -n rabbit@rabbit1 list_users -q | cut -f1)
if ! printf '%s\n' "$users" | grep -Fxq "$RABBITMQ_USER"; then
 rabbitmqctl -n rabbit@rabbit1 add_user "$RABBITMQ_USER" "$RABBITMQ_PASSWORD"
fi
rabbitmqctl -n rabbit@rabbit1 set_user_tags "$RABBITMQ_USER"
rabbitmqctl -n rabbit@rabbit1 set_permissions -p /pedidos360 "$RABBITMQ_USER" '^cmd\.(direct|topic|dead\.dlx)$|^q\.cmd\.(email|kitchen|invoice)(\.dlq)?$' '^cmd\.(direct|topic|dead\.dlx)$|^q\.cmd\.(email|kitchen|invoice)(\.dlq)?$' '^cmd\.(direct|topic|dead\.dlx)$|^q\.cmd\.(email|kitchen|invoice)(\.dlq)?$'
# Definitions contain only the three policies, never users, password hashes or messages.
rabbitmqctl -n rabbit@rabbit1 import_definitions /policies.json
# Import is asynchronous: application dependencies may proceed only after all policies exist.
for attempt in {1..30}; do
 policies=$(rabbitmqctl -n rabbit@rabbit1 list_policies -p /pedidos360 --formatter json)
 if printf '%s' "$policies" | grep -Fq 'policy.email.dlx' && \
    printf '%s' "$policies" | grep -Fq 'policy.kitchen.dlx' && \
    printf '%s' "$policies" | grep -Fq 'policy.invoice.dlx'; then
  exit 0
 fi
 sleep 1
done
echo 'Las tres políticas DLX no quedaron disponibles; aplicaciones deben permanecer detenidas.' >&2
exit 1
