// k6 run performance/k6/gateway-throughput.js   (NOT executed in the build environment: k6 is not installed there)
// Env: GW (default http://localhost:8081), DEMO_API_KEY. Prerequisite: performance/prepare-perf-policy.sh (high limit policy).
// Open-model arrival rate avoids coordinated omission: a slow gateway does not reduce the offered load.
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const GW = __ENV.GW || 'http://localhost:8081';
const KEY = __ENV.DEMO_API_KEY || 'kdemo.local-demo-only-key-0123456789abcdef';
const errors5xx = new Rate('gateway_5xx_rate');
const latency = new Trend('gateway_latency_ms', true);

export const options = {
  scenarios: {
    steady: { executor: 'constant-arrival-rate', rate: 500, timeUnit: '1s', duration: '60s', preAllocatedVUs: 100, maxVUs: 500 },
    ramp: { executor: 'ramping-arrival-rate', startRate: 100, timeUnit: '1s', stages: [{ target: 1000, duration: '60s' }, { target: 0, duration: '10s' }], preAllocatedVUs: 200, maxVUs: 1000, startTime: '70s' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<250', 'p(99)<500'],   // SLO placeholders: derive from your own SLOs, then tighten
    gateway_5xx_rate: ['rate<0.005'],
  },
};

export default function () {
  const res = http.get(`${GW}/gw/orders/v1/orders/1`, { headers: { 'X-API-Key': KEY, 'X-Correlation-Id': `k6-${__VU}-${__ITER}-0000` } });
  latency.add(res.timings.duration);
  errors5xx.add(res.status >= 500);
  check(res, { 'status 200': (r) => r.status === 200 });
}
