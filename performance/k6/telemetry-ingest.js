// k6 run performance/k6/telemetry-ingest.js - observability ingestion: batches of 500 per-minute buckets from simulated gateways.
// Uses the gateway service token (dummy local value). Each batch id is unique; resending verifies idempotency cost.
import http from 'k6/http';
import { check } from 'k6';

const CP = __ENV.CP || 'http://localhost:8080';
const TOKEN = __ENV.GATEWAY_SERVICE_TOKEN || 'local-demo-gateway-token';

export const options = {
  scenarios: { ingest: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '30s', preAllocatedVUs: 20, maxVUs: 100 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] },
};

function batch(id) {
  const buckets = [];
  const now = Date.now();
  for (let i = 0; i < 500; i++) {
    buckets.push({ route: `perf-route-${i % 50}`, bucketStart: new Date(now - (i % 10) * 60000).toISOString().replace(/\.\d+Z$/, 'Z'),
      requests: 100, errors4xx: 1, errors5xx: 0, rateLimited: 0, authFailures: 0, timeouts: 0, latencySumMs: 4000, latencyMaxMs: 90 });
  }
  return JSON.stringify({ batchId: id, buckets, events: [] });
}

export default function () {
  const id = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const res = http.post(`${CP}/internal/v1/telemetry/batches`, batch(id), { headers: { 'Content-Type': 'application/json', 'X-Gateway-Token': TOKEN } });
  check(res, { accepted: (r) => r.status === 202 });
  // note: ids are unique, so buckets with the same (route, minute) in different batches coexist by design (aggregation happens at read time)
}
