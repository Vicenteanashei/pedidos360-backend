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
profiles=()
if [[ "$role" == backend ]]; then
  # DB_URL vacío o apuntando al contenedor "oracle" => Oracle local (perfil local-db).
  # Cualquier otro valor => base externa (RDS for Oracle) y el contenedor no se levanta.
  db_url="$(sed -n 's/^[[:space:]]*DB_URL[[:space:]]*=[[:space:]]*//p' "$env_file" | tail -n 1 | tr -d "\"'")"
  if [[ -z "$db_url" || "$db_url" == *"@//oracle:"* ]]; then
    profiles=(--profile local-db)
    echo 'Base de datos: contenedor Oracle local (perfil local-db).'
  elif [[ "$db_url" =~ ^jdbc:oracle:thin:@//[A-Za-z0-9.-]+:[0-9]{1,5}/[A-Za-z0-9_.$#-]+$ ]]; then
    echo 'Base de datos: Oracle externa (RDS) definida en DB_URL.'
  else
    echo 'DB_URL inválido: usar jdbc:oracle:thin:@//<endpoint>:1521/<servicio>.' >&2
    exit 2
  fi
fi
if [[ "$role" != rabbit ]]; then
  docker buildx version >/dev/null || { echo "Falta Docker Buildx >= 0.17: instalar el plugin oficial antes de construir." >&2; exit 2; }
fi
# Quiet validation: never display resolved environment/secrets.
docker compose --env-file "$env_file" -p "$project" -f "$config" ${profiles[@]+"${profiles[@]}"} config -q
if [[ "$role" == backend ]]; then
  python3 scripts/migrate-rabbit-policies.py --env-file "$env_file" --verify-ready || {
    echo 'Backend no iniciado: preparar Rabbit/políticas o migrar colas existentes según docs/MIGRACION-POLITICAS-RABBIT.md.' >&2
    exit 2
  }
fi
docker compose --env-file "$env_file" -p "$project" -f "$config" ${profiles[@]+"${profiles[@]}"} up -d --build
docker compose --env-file "$env_file" -p "$project" -f "$config" ${profiles[@]+"${profiles[@]}"} ps -a
printf 'Contenedores solicitados. Verificar salud, JWT, colas y persistencia segun la guia; no se declara despliegue probado.\n'
