// SOLO PRUEBAS LOCALES. Emisor OIDC falso: GET /token?roles=Admin,Auditor&sub=usuario. Nunca usar fuera de localhost.
import http from 'node:http';
import {generateKeyPairSync, createSign} from 'node:crypto';
const ISS = 'http://127.0.0.1:9099', AUD = 'local-test', KID = 'local-1';
const {privateKey, publicKey} = generateKeyPairSync('rsa', {modulusLength: 2048});
const jwk = {...publicKey.export({format: 'jwk'}), kid: KID, use: 'sig', alg: 'RS256'};
const b64 = o => Buffer.from(typeof o === 'string' ? o : JSON.stringify(o)).toString('base64url');
function token(roles, sub) {
  const now = Math.floor(Date.now() / 1000);
  const head = b64({alg: 'RS256', typ: 'JWT', kid: KID});
  const body = b64({iss: ISS, aud: AUD, sub, oid: sub, name: sub, preferred_username: sub + '@test.local',
    scp: 'access_as_user', roles, iat: now, nbf: now - 5, exp: now + 3600});
  const sig = createSign('RSA-SHA256').update(head + '.' + body).sign(privateKey).toString('base64url');
  return head + '.' + body + '.' + sig;
}
http.createServer((req, res) => {
  const u = new URL(req.url, ISS);
  const send = (o, t = 'application/json') => { res.writeHead(200, {'Content-Type': t}); res.end(typeof o === 'string' ? o : JSON.stringify(o)); };
  if (u.pathname === '/.well-known/openid-configuration')
    return send({issuer: ISS, jwks_uri: ISS + '/jwks', id_token_signing_alg_values_supported: ['RS256'],
      subject_types_supported: ['public'], response_types_supported: ['id_token'], authorization_endpoint: ISS + '/a', token_endpoint: ISS + '/t'});
  if (u.pathname === '/jwks') return send({keys: [jwk]});
  if (u.pathname === '/token')
    return send(token((u.searchParams.get('roles') || 'Admin').split(','), u.searchParams.get('sub') || 'admin-test'), 'text/plain');
  res.writeHead(404); res.end();
}).listen(9099, '127.0.0.1', () => console.log('issuer on 9099'));
