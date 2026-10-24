// k6 run performance/k6/control-plane-read.js - registry list/search: JWT validation + JPA query + pagination.
import http from 'k6/http';
import { check } from 'k6';

const CP = __ENV.CP || 'http://localhost:8080';
const PASSWORD = __ENV.DEV_AUTH_PASSWORD || 'local-demo-password';

export const options = {
  scenarios: { reads: { executor: 'constant-arrival-rate', rate: 200, timeUnit: '1s', duration: '45s', preAllocatedVUs: 50, maxVUs: 200 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<300'] },
};

export function setup() {
  const r = http.post(`${CP}/api/v1/auth/token`, JSON.stringify({ username: 'viewer', password: PASSWORD }), { headers: { 'Content-Type': 'application/json' } });
  return { token: r.json('accessToken') };
}

export default function (data) {
  const h = { headers: { Authorization: `Bearer ${data.token}` } };
  const res = http.get(`${CP}/api/v1/apis?size=20&sort=name`, h);
  check(res, { 'list 200': (r) => r.status === 200 });
  // anonymous request: measures the rejection path (authentication overhead without data access)
  const anon = http.get(`${CP}/api/v1/apis`);
  check(anon, { 'anonymous 401': (r) => r.status === 401 });
}
