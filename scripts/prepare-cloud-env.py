#!/usr/bin/env python3
"""Produce per-host private env files locally, after cloud URLs/IP are known. No upload."""
from pathlib import Path
import argparse, ipaddress, os, re, shlex
from urllib.parse import urlsplit
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--frontend-url',required=True); p.add_argument('--api-url',required=True); p.add_argument('--rabbit-ip',required=True)
p.add_argument('--source',type=Path,default=root/'infra/.env')
p.add_argument('--output',type=Path,default=root.parent/'entregables/preparacion-martes/privado')
a=p.parse_args()
for value in [a.frontend_url,a.api_url]:
    url=urlsplit(value)
    if url.scheme!='https' or not url.hostname or url.username or url.password or url.query or url.fragment or re.search(r'[\r\n]',value):p.error('Usar URLs HTTPS reales sin credenciales/query/fragmento.')
if urlsplit(a.frontend_url).path not in {'','/'}:p.error('Frontend debe ser origen HTTPS sin subruta.')
ip=ipaddress.ip_address(a.rabbit_ip)
if ip.version!=4 or not any(ip in ipaddress.ip_network(n) for n in ['10.0.0.0/8','172.16.0.0/12','192.168.0.0/16']):p.error('Rabbit requiere IP privada IPv4 RFC1918.')
values={}
for line in a.source.read_text().splitlines():
    if not line.strip() or line.lstrip().startswith('#'):continue
    key,raw=line.split('=',1);raw=raw.strip()
    if raw.startswith(('"',"'")):
        parts=shlex.split(raw,comments=True);values[key.strip()]=parts[0] if parts else ''
    else:values[key.strip()]=raw
required=['ENTRA_TENANT_ID','SPA_CLIENT_ID','API_CLIENT_ID','ORACLE_ADMIN_PASSWORD','ORDERS_DB_PASSWORD','CATALOG_DB_PASSWORD','NOTIFY_DB_PASSWORD','INTERNAL_SERVICE_KEY','RABBITMQ_USER','RABBITMQ_PASSWORD','RABBITMQ_COOKIE','RABBIT_ADMIN_USER','RABBIT_ADMIN_PASSWORD']
if any(not values.get(k) for k in required):p.error('Faltan claves en el .env de origen; no se imprimen valores.')
backend={k:values[k] for k in required if k not in {'RABBITMQ_COOKIE'}}
backend.update(RABBITMQ_ADDRESSES=f'{ip}:5672,{ip}:5673,{ip}:5674',RABBIT_MANAGEMENT_URL=f'http://{ip}:15672',BFF_BIND_ADDRESS='0.0.0.0',CORS_ALLOWED_ORIGINS=a.frontend_url.rstrip('/'),REPORT_TIME_ZONE='America/Santiago',MAIL_ENABLED='false',DEMO_FAILURE_ENABLED='false',CATALOG_PROFILE='demo')
frontend={k:values[k] for k in ['ENTRA_TENANT_ID','SPA_CLIENT_ID','API_CLIENT_ID']}
frontend.update(PUBLIC_API_URL=a.api_url.rstrip('/'),FRONTEND_BIND_ADDRESS='0.0.0.0',FRONTEND_PORT='80',VITE_DIAGNOSTICS='true')
rabbit={k:values[k] for k in ['RABBITMQ_USER','RABBITMQ_PASSWORD','RABBITMQ_COOKIE','RABBIT_ADMIN_USER','RABBIT_ADMIN_PASSWORD']}
rabbit.update(MQ_BIND_ADDRESS=str(ip),MQ_MANAGEMENT_BIND_ADDRESS=str(ip))
a.output.mkdir(parents=True,exist_ok=True);os.chmod(a.output,0o700)
for role,env in [('backend',backend),('frontend',frontend),('rabbit',rabbit)]:
    # Compose single quotes keep dollar signs literal. Generated local secrets have no apostrophes.
    if any("'" in v or re.search(r'[\r\n]',v) for v in env.values()):p.error('Valores con comillas simples/nuevas lineas requieren configuracion manual; no se crean archivos.')
for role,env in [('backend',backend),('frontend',frontend),('rabbit',rabbit)]:
    dest=a.output/f'{role}.env'
    fd=os.open(dest,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as f:f.write('\n'.join(f"{key}='{value}'" for key,value in env.items())+'\n')
    os.chmod(dest,0o600)
print('Configuracion privada por host lista en',a.output,'; no se subio a AWS ni se incluyo en los paquetes.')
