import {readFile, writeFile, chmod} from 'node:fs/promises';

// Read the token from stdin (not an argument, log, file, or third-party service).
const args = process.argv.slice(2);
const [method = 'GET', path = '/api/data', bodyFile] = args;
const base = new URL(process.env.P360_API_URL || 'http://localhost:8080');
if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash)
  throw new Error('P360_API_URL debe ser una URL base sin credenciales, query ni fragmento.');
if (base.protocol !== 'https:' && !['localhost', '127.0.0.1', '[::1]'].includes(base.hostname))
  throw new Error('Usa HTTPS para enviar un Bearer fuera de localhost.');
if (!/^(GET|POST|PUT|DELETE)$/.test(method) || !path.startsWith('/api/') || path.includes('://') || path.startsWith('//'))
  throw new Error('Uso: node scripts/manual-api.mjs GET /api/data [archivo.json]');
const target = new URL(base.href.replace(/\/$/, '') + path);
if (target.origin !== base.origin) throw new Error('Destino diferente de P360_API_URL.');
const headers = {'X-Correlation-Id': 'manual-' + crypto.randomUUID()};
if (process.env.P360_NO_TOKEN !== 'true') {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  const token = Buffer.concat(chunks).toString().trim();
  if (!token || /\s/.test(token)) throw new Error('Entrega el access token por stdin.');
  headers.Authorization = 'Bearer ' + token;
}
const body = bodyFile ? await readFile(bodyFile, 'utf8') : undefined;
if (body !== undefined) {
  if (process.env.P360_RAW_BODY !== 'true') JSON.parse(body);
  headers['Content-Type'] = 'application/json';
}
const expected = process.env.P360_EXPECT_STATUS;
if (expected && !/^[1-5][0-9]{2}$/.test(expected)) throw new Error('P360_EXPECT_STATUS debe ser un estado HTTP de tres dígitos.');
const response = await fetch(target, {method, headers, body, redirect:'error', signal:AbortSignal.timeout(20000)});
console.log('HTTP', response.status);
console.log('X-Correlation-Id:', response.headers.get('X-Correlation-Id') || headers['X-Correlation-Id']);
const bytes = Buffer.from(await response.arrayBuffer());
const file = process.env.P360_RESPONSE_FILE;
if (file) {
  await writeFile(file, bytes, {mode:0o600});
  await chmod(file, 0o600);
  const meta = {status:response.status, method, path, bytes:bytes.length, headers:{}};
  for (const key of ['content-type','content-disposition','x-correlation-id'])
    if (response.headers.has(key)) meta.headers[key] = response.headers.get(key);
  await writeFile(file + '.meta.json', JSON.stringify(meta, null, 2) + '\n', {mode:0o600});
  await chmod(file + '.meta.json', 0o600);
  console.log('Respuesta guardada:', file);
}
const raw = bytes.toString('utf8');
if (!response.headers.has('content-disposition')) {
  try { console.log(JSON.stringify(JSON.parse(raw), null, 2)); } catch { console.log(raw); }
} else console.log('Documento:', response.headers.get('content-disposition'), '(' + bytes.length + ' bytes)');
if (expected ? response.status !== Number(expected) : !response.ok) process.exitCode = 1;
