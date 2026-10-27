#!/usr/bin/env python3
"""Fail closed before publishing. Never display a matched secret or save an unredacted report."""
import argparse,json,os,re,shutil,subprocess,tempfile
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--staged',action='store_true');p.add_argument('--history',action='store_true');a=p.parse_args()
root=Path(subprocess.check_output(['git','rev-parse','--show-toplevel'],text=True).strip())
def git(*args):return subprocess.check_output(['git','-C',str(root),*args])
names=(git('diff','--cached','--name-only','--diff-filter=ACMR','-z') if a.staged else git('ls-files','--cached','--others','--exclude-standard','-z')).decode().split('\0')
patterns={
    'private-key':re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----'),
    'aws-access-id':re.compile(rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'jwt-literal':re.compile(rb'eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}'),
    'project-password-literal':re.compile(rb'pedidos[a-z]\.\d{3,}\.?',re.I),
    'personal-course-account':re.compile(rb'@[a-z0-9.-]*duocuc\.cl\b',re.I),
}
known=[]
private=os.environ.get('P360_PRIVATE_ENV_PATH')
if private:
    for line in Path(private).read_text().splitlines():
        if line.startswith('#') or '=' not in line:continue
        key,value=line.split('=',1);value=value.strip().strip('\'"')
        if re.search(r'PASSWORD|SECRET|COOKIE|INTERNAL_SERVICE_KEY',key) and len(value)>=8:known.append(value.encode())
blocked={'.pem','.key','.p12','.pfx','.ppk','.eml','.pdf','.log','.db','.sqlite','.zip','.rar','.gz','.tar'}
scanner=os.environ.get('GITLEAKS_BIN') or shutil.which('gitleaks')
# GUI Git clients may not inherit the interactive shell's PATH.
if not scanner:
    user_scanner=Path.home()/'.local/bin/gitleaks'
    if user_scanner.is_file() and os.access(user_scanner,os.X_OK):scanner=str(user_scanner)
if not scanner or not Path(scanner).is_file() or not os.access(scanner,os.X_OK):raise SystemExit('Falta Gitleaks ejecutable. Instalar desde fuente oficial o definir GITLEAKS_BIN; publicación bloqueada.')
findings=[];count=0;history_blobs=0
if a.history:
    objects={}
    for line in git('rev-list','--objects','--all').decode().splitlines():
        fields=line.split(' ',1)
        if len(fields)==2:objects[fields[0]]=fields[1]
    batch=subprocess.run(['git','-C',str(root),'cat-file','--batch'],input=('\n'.join(objects)+'\n').encode(),capture_output=True,check=True).stdout
    position=0
    while position<len(batch):
        end=batch.index(b'\n',position);fields=batch[position:end].split();size=int(fields[2]);start=end+1;raw=batch[start:start+size];position=start+size+1
        if fields[1]!=b'blob':continue
        history_blobs+=1;name=objects[fields[0].decode()]
        for rule,pattern in patterns.items():
            match=pattern.search(raw)
            if match:findings.append({'file':name,'rule':rule,'line':raw[:match.start()].count(b'\n')+1,'scope':'history'})
        if any(secret in raw for secret in known):findings.append({'file':name,'rule':'known-private-env-value','scope':'history'})
with tempfile.TemporaryDirectory(prefix='p360-public-scan-') as tmp:
    snapshot=Path(tmp)/'source';snapshot.mkdir(mode=0o700)
    for name in sorted(set(n for n in names if n)):
        path=Path(name)
        if path.is_absolute() or '..' in path.parts:raise SystemExit('Ruta Git inválida; publicación bloqueada.')
        filename=path.name.lower()
        if (filename.startswith('.env') and filename!='.env.example') or path.suffix.lower() in blocked or any(part in {'.aws','.azure','.ssh','manual-results','data','node_modules','target','dist'} for part in path.parts):
            findings.append({'file':name,'rule':'private-file-or-generated-data'});continue
        if any(x in filename for x in ['credenciales','apikeys','credentials']) and path.suffix=='.txt':
            findings.append({'file':name,'rule':'credential-file'});continue
        if a.staged:
            mode=git('ls-files','--stage','--',name).decode().split(' ',1)[0]
            if mode in {'120000','160000'}:findings.append({'file':name,'rule':'symlink-or-submodule'});continue
            raw=git('show',':'+name)
        else:
            original=root/path
            if original.is_symlink():findings.append({'file':name,'rule':'symlink'});continue
            if not original.is_file():continue
            raw=original.read_bytes()
        count+=1
        for rule,pattern in patterns.items():
            match=pattern.search(raw)
            if match:findings.append({'file':name,'rule':rule,'line':raw[:match.start()].count(b'\n')+1})
        if any(secret in raw for secret in known):findings.append({'file':name,'rule':'known-private-env-value'})
        dest=snapshot/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
    report=Path(tmp)/'gitleaks.json'
    mode=['git','--log-opts=--all',str(root)] if a.history else ['dir',str(snapshot)]
    command=[scanner,*mode,'--redact=100','--no-banner','--report-format=json','--report-path='+str(report)]
    if (root/'.gitleaksignore').is_file():command+=['--gitleaks-ignore-path='+str(root/'.gitleaksignore')]
    result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode not in {0,1}:raise SystemExit('El escáner falló; publicación bloqueada. No se muestra salida que pudiera contener valores privados.')
    if report.is_file():
        for entry in json.loads(report.read_text()):
            findings.append({'file':entry['File'],'rule':entry['RuleID'],'line':entry['StartLine'],'commit':entry.get('Commit','')[:8]})
if findings:
    print(json.dumps({'status':'BLOCKED','filesChecked':count,'findings':findings},ensure_ascii=False,indent=2));raise SystemExit(1)
print(json.dumps({'status':'PASS','filesChecked':count,'staged':a.staged,'history':a.history,'historyBlobsChecked':history_blobs,'limitations':'Detección automática, no garantía absoluta; no compartir evidencias privadas.'},ensure_ascii=False))
