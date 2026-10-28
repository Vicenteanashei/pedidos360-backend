#!/usr/bin/env python3
"""Crea (o sincroniza) los usuarios de Pedidos360 en RDS for Oracle.

Equivale a infra/oracle/01-users.sh, que solo corre dentro del contenedor Oracle local.
Se ejecuta UNA vez desde la EC2 de backend (debe llegar al puerto 1521 de RDS):

    python3 -m pip install --user oracledb
    python3 scripts/init-rds-users.py --env-file infra/.env --master-user admin

Lee DB_URL y las contraseñas P360 del .env; la contraseña maestra de RDS se pide oculta
y nunca se guarda. Es idempotente: si un usuario ya existe solo actualiza su contraseña.
Las tablas no se crean aquí: Flyway las crea al iniciar cada microservicio.
"""
import argparse
import getpass
import re
import shlex
import sys
from pathlib import Path

JDBC = re.compile(r"^jdbc:oracle:thin:@//(?P<host>[A-Za-z0-9.-]+):(?P<port>\d{1,5})/(?P<service>[A-Za-z0-9_.$#-]+)$")
# Mismo formato que exige 01-users.sh; también evita inyección en el DDL (no admite binds).
PASSWORD = re.compile(r"^[a-f0-9]{32,64}$")
USERS = {
    "P360_ORDERS": "ORDERS_DB_PASSWORD",
    "P360_CATALOG": "CATALOG_DB_PASSWORD",
    "P360_NOTIFY": "NOTIFY_DB_PASSWORD",
}
GRANTS = "CREATE SESSION, CREATE TABLE, CREATE SEQUENCE"


def read_env(path: Path) -> dict:
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, raw = line.split("=", 1)
        raw = raw.strip()
        if raw.startswith(("'", '"')):
            parts = shlex.split(raw, comments=True)
            raw = parts[0] if parts else ""
        values[key.strip()] = raw
    return values


def plan(env: dict) -> tuple[dict, dict]:
    """Valida el .env y devuelve (destino, {usuario: contraseña}) sin conectarse."""
    match = JDBC.match(env.get("DB_URL", ""))
    if not match:
        raise ValueError("DB_URL debe ser jdbc:oracle:thin:@//<endpoint-rds>:1521/<servicio>")
    if "@//oracle:" in env["DB_URL"]:
        raise ValueError("DB_URL apunta al contenedor local; este script es solo para RDS")
    passwords = {}
    for user, key in USERS.items():
        value = env.get(key, "")
        if not PASSWORD.match(value):
            raise ValueError(f"{key} falta o no tiene el formato generado (hex de 32 a 64 caracteres)")
        passwords[user] = value
    target = {"host": match["host"], "port": int(match["port"]), "service_name": match["service"]}
    return target, passwords


def statements(user: str, password: str, exists: bool) -> list[str]:
    verb = "ALTER" if exists else "CREATE"
    return [
        f'{verb} USER {user} IDENTIFIED BY "{password}"',
        f"GRANT {GRANTS} TO {user}",
        f"ALTER USER {user} QUOTA UNLIMITED ON USERS",
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--env-file", type=Path, default=Path("infra/.env"))
    parser.add_argument("--master-user", default="admin", help="usuario maestro definido al crear la instancia RDS")
    parser.add_argument("--dry-run", action="store_true", help="valida el .env y muestra el plan sin conectarse")
    args = parser.parse_args()

    try:
        target, passwords = plan(read_env(args.env_file))
    except (OSError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 2

    print(f"Destino: {target['host']}:{target['port']}/{target['service_name']}")
    if args.dry_run:
        for user in passwords:
            print(f"  {user}: CREATE o ALTER USER, GRANT {GRANTS}, QUOTA en USERS")
        return 0

    try:
        import oracledb  # modo thin: no requiere Oracle Instant Client
    except ImportError:
        print("Falta el driver: python3 -m pip install --user oracledb", file=sys.stderr)
        return 2

    master_password = getpass.getpass(f"Contraseña maestra de RDS para {args.master_user}: ")
    try:
        with oracledb.connect(user=args.master_user, password=master_password, **target) as conn:
            with conn.cursor() as cur:
                for user, password in passwords.items():
                    cur.execute("SELECT COUNT(*) FROM dba_users WHERE username = :u", u=user)
                    exists = cur.fetchone()[0] > 0
                    for sql in statements(user, password, exists):
                        cur.execute(sql)
                    print(f"  {user}: {'contraseña sincronizada' if exists else 'creado'} con permisos")
    except oracledb.Error as error:
        # Solo el código ORA, nunca credenciales.
        print(f"Error de Oracle: {error.args[0].full_code if error.args else type(error).__name__}", file=sys.stderr)
        return 1
    print("Listo. Al iniciar, Flyway crea las tablas de cada microservicio.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
