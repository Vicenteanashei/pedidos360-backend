#!/usr/bin/env python3
"""Add missing private values; preserve existing credentials. Never prints values."""
import argparse
import os
from pathlib import Path
import secrets

parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "infra/.env")
args = parser.parse_args()
example = Path(__file__).resolve().parents[1] / "infra/.env.example"
text = args.output.read_text(encoding="utf-8") if args.output.exists() else example.read_text(encoding="utf-8")
secret_keys = {
    "ORACLE_ADMIN_PASSWORD", "ORDERS_DB_PASSWORD", "CATALOG_DB_PASSWORD",
    "NOTIFY_DB_PASSWORD", "REPORT_DB_PASSWORD", "AUDIT_DB_PASSWORD",
    "INTERNAL_SERVICE_KEY", "RABBITMQ_PASSWORD", "RABBITMQ_COOKIE",
    "RABBIT_ADMIN_PASSWORD", "KAFKA_UI_PASSWORD",
}
lines = text.splitlines()
existing = {}
for index, line in enumerate(lines):
    if "=" in line and not line.lstrip().startswith("#"):
        key, value = line.split("=", 1)
        existing[key.strip()] = (index, value.strip())
for key in sorted(secret_keys):
    if key in existing and existing[key][1].strip().strip(chr(34)).strip(chr(39)):
        continue
    line = f"{key}={secrets.token_hex(24)}"
    if key in existing and existing[key][1].strip().strip(chr(34)).strip(chr(39)):
        lines[existing[key][0]] = line
    else:
        lines.append(line)
args.output.parent.mkdir(parents=True, exist_ok=True)
fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as handle:
    handle.write("\n".join(lines) + "\n")
os.chmod(args.output, 0o600)
print("Configuración privada actualizada; credenciales existentes conservadas.")

