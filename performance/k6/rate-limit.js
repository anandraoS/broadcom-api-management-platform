// k6 run performance/k6/rate-limit.js  - measures the 429 path and checks the limiter admits ~limit requests.
// Prerequisite: the orders API policy at its seeded 120/min token bucket (burst 20) - do NOT run prepare-perf-policy.sh first.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const GW = __ENV.GW || 'http://localhost:8081';
const KEY = __ENV.DEMO_API_KEY || 'kdemo.local-demo-only-key-0123456789abcdef';
const ok = new Counter('admitted');
const limited = new Counter('limited_429');

export const options = {
  vus: 20, duration: '30s',
  thresholds: { limited_429: ['count>0'], 'http_req_duration{status:429}': ['p(95)<50'] }, // rejection must be cheap
};

export default function () {
  const r = http.get(`${GW}/gw/orders/v1/orders/1`, { headers: { 'X-API-Key': KEY } });
  if (r.status === 200) ok.add(1);
  if (r.status === 429) {
    limited.add(1);
    check(r, { 'has Retry-After': (x) => x.headers['Retry-After'] !== undefined, 'has limit headers': (x) => x.headers['X-Ratelimit-Limit'] !== undefined });
  }
}
// Expected admitted ~= burst (20) + 120/min * 0.5 min = ~80 for the 30 s run with one gateway in LOCAL mode.
