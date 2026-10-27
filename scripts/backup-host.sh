#!/usr/bin/env bash
# Offline volume backup for one deployment host; never deletes a volume or restores over it.
set -euo pipefail
role="${1:-}"
case "$role" in backend) config=infra/backend/compose.yml; project=pedidos360-ea2-backend;; rabbit) config=infra/mq/compose.yml; project=pedidos360-ea2-mq;; *) echo 'Uso: bash scripts/backup-host.sh backend|rabbit DIRECTORIO_DESTINO [archivo.env]' >&2; exit 2;; esac
[[ $# -ge 2 ]] || exit 2
destination="$(realpath -m -- "$2")"
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
env_file="${3:-infra/.env}"
[[ -f "$env_file" ]] || { echo 'Falta configuracion privada de este host.' >&2; exit 2; }
umask 077
mkdir -p -- "$destination"
backup="$destination/pedidos360-$role-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -- "$backup"
dc() { docker compose --env-file "$env_file" -p "$project" -f "$config" "$@"; }
mapfile -t running < <(dc ps --status running --services)
mapfile -t containers < <(dc ps -a -q)
[[ ${#containers[@]} -gt 0 ]] || { echo 'No se encontraron contenedores del proyecto.' >&2; exit 2; }
mapfile -t volumes < <(docker inspect --format '{{range .Mounts}}{{if eq .Type "volume"}}{{.Name}}{{"\n"}}{{end}}{{end}}' "${containers[@]}" | sort -u | sed '/^$/d')
[[ ${#volumes[@]} -gt 0 ]] || exit 2
# Obtain the official helper image before stopping the application.
docker pull busybox:1.37 >/dev/null
restart_services() { if [[ ${#running[@]} -gt 0 ]]; then dc start "${running[@]}"; fi; }
trap restart_services EXIT
if [[ ${#running[@]} -gt 0 ]]; then dc stop --timeout 120 "${running[@]}"; fi
for volume in "${volumes[@]}"; do
  docker run --rm -v "$volume:/volume:ro" -v "$backup:/backup" busybox:1.37 tar -czf "/backup/$volume.tar.gz" -C /volume .
done
printf '%s\n' "${volumes[@]}" > "$backup/volumes.txt"
(cd -- "$backup" && sha256sum ./*.tar.gz > SHA256SUMS)
printf 'Backup privado offline: %s. Conservar .env aparte; restaurar solo en volumen/proyecto aislado.\n' "$backup"
