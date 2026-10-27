# Cargar con: source scripts/manual-session.sh (Bash, desde la raíz de la copia).
# Los tokens quedan en variables del shell, sin exportar ni escribir en disco.
P360_MANUAL_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
umask 077
mkdir -p manual-results

p360_login() {
  local role="${1:-}" value
  case "$role" in admin|operador|cliente|auditor|otro_cliente) ;; *) echo 'Rol: admin, operador, cliente, auditor u otro_cliente' >&2; return 2;; esac
  read -r -s -p "Pega el access token de $role: " value
  printf '\n' >&2
  [[ -n "$value" && "$value" != *[[:space:]]* ]] || { echo 'Token vacío o con espacios.' >&2; return 2; }
  printf -v "P360_MANUAL_TOKEN_$role" '%s' "$value"
  export -n "P360_MANUAL_TOKEN_$role"
  unset value
}

p360() {
  local role="${1:-}" expected="${2:-}" token_var file
  [[ $# -ge 4 ]] || { echo 'Uso: p360 rol HTTP_ESPERADO MÉTODO /api/ruta [body.json]' >&2; return 2; }
  shift 2
  case "$role" in admin|operador|cliente|auditor|otro_cliente) ;; *) echo 'Rol desconocido.' >&2; return 2;; esac
  token_var="P360_MANUAL_TOKEN_$role"
  [[ -n "${!token_var:-}" ]] || { echo "Primero: p360_login $role" >&2; return 2; }
  file="manual-results/${P360_SAVE_NAME:-$(date +%s%N)-$role}.response"
  printf '%s' "${!token_var}" | P360_NO_TOKEN=false P360_EXPECT_STATUS="$expected" P360_RESPONSE_FILE="$file" node "$P360_MANUAL_SCRIPT_DIR/manual-api.mjs" "$@"
}

p360_logout() {
  unset P360_MANUAL_TOKEN_admin P360_MANUAL_TOKEN_operador P360_MANUAL_TOKEN_cliente P360_MANUAL_TOKEN_auditor P360_MANUAL_TOKEN_otro_cliente
}
