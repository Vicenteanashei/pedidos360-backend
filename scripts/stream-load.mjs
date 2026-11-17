// Run explicitly against a lab environment. Token comes from an existing private environment.
import {performance} from 'node:perf_hooks';
const base = (process.env.P360_API_URL || '').replace(/\/$/, '');
const token = process.env.P360_ACCESS_TOKEN;
const count = Number(process.env.P360_LOAD_COUNT || '20');
const productId = Number(process.env.P360_PRODUCT_ID);
const storeId = Number(process.env.P360_STORE_ID || '1');
if (!base.startsWith('https://') && !/^http:\/\/(localhost|127\.0\.0\.1)(:|\/|$)/.test(base))
  throw new Error('Configura P360_API_URL con HTTPS o localhost.');
if (!token || !Number.isSafeInteger(productId) || productId < 1 ||
    !Number.isSafeInteger(count) || count < 1 || count > 5000 ||
    !Number.isSafeInteger(storeId) || storeId < 1 || storeId > 20)
  throw new Error('Configura token, producto, local y una cantidad entre 1 y 5000; no se imprimen credenciales.');
const headers = {Authorization: 'Bearer ' + token, 'Content-Type': 'application/json'};
async function groups() {
  const response = await fetch(base + '/api/kafka/groups', {headers, signal: AbortSignal.timeout(15000)});
  if (!response.ok) throw new Error('La medición de lag requiere Admin y Kafka Admin disponible.');
  const ids = await response.json();
  return await Promise.all(ids.filter(id => id.startsWith('p360-')).map(async id => {
    const response = await fetch(base + '/api/kafka/groups/' + encodeURIComponent(id), {headers, signal: AbortSignal.timeout(15000)});
    if (!response.ok) throw new Error('No se pudo medir el grupo.');
    return response.json();
  }));
}
const before = await groups();
const durations = [], statuses = {};
let accepted = 0;
const start = performance.now();
for (let offset = 0; offset < count; offset += 5) {
  await Promise.all(Array.from({length: Math.min(5, count - offset)}, async () => {
    const begin = performance.now();
    try {
      const response = await fetch(base + '/api/orders', {method: 'POST', headers,
        body: JSON.stringify({storeId, customerName: 'Prueba de carga EA3', customerEmail: 'load@example.test',
          notes: 'LAB: prueba de streaming', items: [{productId, quantity: 1}]}),
        signal: AbortSignal.timeout(30000)});
      statuses[response.status] = (statuses[response.status] || 0) + 1;
      if (response.status === 201) accepted++;
      await response.arrayBuffer();
    } catch {
      statuses.networkError = (statuses.networkError || 0) + 1;
    } finally { durations.push(performance.now() - begin); }
  }));
}
const elapsedMs = performance.now() - start;
const after = await groups();
durations.sort((a, b) => a - b);
console.log(JSON.stringify({requested: count, httpAccepted: accepted, statuses, elapsedMs,
  httpRequestsPerSecond: count / (elapsedMs / 1000),
  httpLatencyMeanMs: durations.reduce((a, b) => a + b, 0) / durations.length,
  httpLatencyP95Ms: durations[Math.min(durations.length - 1, Math.ceil(durations.length * 0.95) - 1)],
  consumerGroupsBefore: before, consumerGroupsAfter: after,
  limitation: 'HTTP acceptance is not Kafka confirmation or projection completion. Compare offsets, outbox and lag after draining.'}, null, 2));

