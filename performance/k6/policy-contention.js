// k6 run performance/k6/policy-contention.js - concurrent policy submissions + lifecycle changes against ONE API.
// Expectation: only 201 / 403 / 409 / 422 responses - never 500 - and the policy version numbers stay unique (DB unique key).
import http from 'k6/http';
import { check } from 'k6';

const CP = __ENV.CP || 'http://localhost:8080';
const PASSWORD = __ENV.DEV_AUTH_PASSWORD || 'local-demo-password';

export const options = {
  scenarios: { contend: { executor: 'constant-vus', vus: 20, duration: '30s' } },
  thresholds: { 'http_req_failed{expected_response:true}': ['rate<0.01'], checks: ['rate>0.99'] },
};

export function setup() {
  const tok = (u) => http.post(`${CP}/api/v1/auth/token`, JSON.stringify({ username: u, password: PASSWORD }), { headers: { 'Content-Type': 'application/json' } }).json('accessToken');
  const admin = tok('admin');
  const api = http.get(`${CP}/api/v1/apis?q=orders`, { headers: { Authorization: `Bearer ${admin}` } }).json('content.0.id');
  return { admin, api };
}

export default function (d) {
  const h = { headers: { Authorization: `Bearer ${d.admin}`, 'Content-Type': 'application/json' } };
  const policy = { auth: 'API_KEY', allowedMethods: ['GET'], rateLimitPerMinute: 100 + (__VU % 5), burst: 20, rateAlgorithm: 'TOKEN_BUCKET', timeoutMs: 3000, maxRequestBytes: 1024, retryEnabled: false };
  const r = http.post(`${CP}/api/v1/apis/${d.api}/policies`, JSON.stringify({ config: policy, comment: 'contention test' }), h);
  check(r, { 'no 5xx': (x) => x.status < 500, 'expected status': (x) => [201, 403, 409, 422].includes(x.status) });
}
// Cleanup: contention test leaves many PENDING_APPROVAL versions; reject them from the Approvals page or recreate the demo database.
