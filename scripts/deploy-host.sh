#!/usr/bin/env bash
# Run in EC2 Instance Connect after extracting only this host's source archive.
set -euo pipefail
role="${1:-}"
case "$role" in backend) config=infra/backend/compose.yml; project=pedidos360-ea2-backend;; frontend) config=infra/frontend/compose.yml; project=pedidos360-ea2-frontend;; rabbit) config=infra/mq/compose.yml; project=pedidos360-ea2-mq;; *) echo 'Uso: bash scripts/deploy-host.sh backend|frontend|rabbit [archivo.env]' >&2; exit 2;; esac
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
env_file="${2:-infra/.env}"
[[ -f "$env_file" ]] || { echo 'Falta el archivo privado .env de este host; transferirlo por separado.' >&2; exit 2; }
chmod 600 "$env_file"
docker info >/dev/null
docker compose version
if [[ "$role" != rabbit ]]; then
  docker buildx version >/dev/null || { echo "Falta Docker Buildx >= 0.17: instalar el plugin oficial antes de construir." >&2; exit 2; }
fi
# Quiet validation: never display resolved environment/secrets.
docker compose --env-file "$env_file" -p "$project" -f "$config" config -q
if [[ "$role" == backend ]]; then
  python3 scripts/migrate-rabbit-policies.py --env-file "$env_file" --verify-ready || {
    echo 'Backend no iniciado: preparar Rabbit/políticas o migrar colas existentes según docs/MIGRACION-POLITICAS-RABBIT.md.' >&2
    exit 2
  }
fi
docker compose --env-file "$env_file" -p "$project" -f "$config" up -d --build
docker compose --env-file "$env_file" -p "$project" -f "$config" ps -a
printf 'Contenedores solicitados. Verificar salud, JWT, colas y persistencia segun la guia; no se declara despliegue probado.\n'
