import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { StateView } from '../components/ui';
import { api } from '../core/api';
import { useAsync } from '../core/useAsync';

/**
 * Traffic dashboard. Data comes from per-minute aggregates shipped by gateways, so it lags real traffic by up to about
 * a minute plus the shipper interval; use Grafana for second-level resolution and true latency percentiles.
 */
export default function DashboardPage() {
  const [windowMin, setWindowMin] = useState(15);
  const state = useAsync((s) => api.traffic(windowMin, s), [windowMin]);
  const { reload } = state;

  useEffect(() => { const t = setInterval(reload, 30_000); return () => clearInterval(t); }, [reload]);

  const d = state.data;
  const totals = (d?.routes ?? []).reduce((a, r) => ({ rl: a.rl + r.rateLimited, auth: a.auth + r.authFailures }), { rl: 0, auth: 0 });
  const throughput = d ? d.totalRequests / (d.windowMinutes * 60) : 0;
  const pct = (x: number) => `${(x * 100).toFixed(1)}%`;

  return (
    <>
      <h1 className="page-title">Traffic dashboard</h1>
      <div className="toolbar">
        <label className="field">Window
          <select value={windowMin} onChange={(e) => setWindowMin(Number(e.target.value))} aria-label="Time window">
            {[5, 15, 60, 240].map((w) => <option key={w} value={w}>Last {w} min</option>)}
          </select>
        </label>
        <span className="spacer" />
        <span className="muted">Auto-refresh every 30 s. Percentiles: see Grafana (gateway dashboard).</span>
      </div>
      <StateView loading={state.loading} error={state.error} empty={!d?.routes.length}
                 emptyText="No traffic reported in this window yet. Send requests through the gateway and wait for the next telemetry flush.">
        {d && (
          <>
            <div className="cards" data-testid="kpis">
              <div className="card"><div className="muted">Requests</div><div className="kpi">{d.totalRequests.toLocaleString()}</div></div>
              <div className="card"><div className="muted">5xx error rate</div><div className={`kpi${d.overallErrorRate > 0.05 ? ' bad' : ''}`}>{pct(d.overallErrorRate)}</div></div>
              <div className="card"><div className="muted">Throughput</div><div className="kpi">{throughput.toFixed(1)}/s</div></div>
              <div className="card"><div className="muted">Rate-limited / auth failures</div><div className="kpi">{totals.rl.toLocaleString()} / {totals.auth.toLocaleString()}</div></div>
            </div>
            <h2>By route</h2>
            <div className="table-wrap">
              <table aria-label="Route metrics">
                <thead><tr><th>Route</th><th>Requests</th><th>5xx rate</th><th>Avg latency</th><th>Max latency</th><th>429</th><th>401</th><th>Timeouts</th><th>5xx share</th></tr></thead>
                <tbody>
                  {d.routes.map((r) => (
                    <tr key={r.route}>
                      <td><Link to="/incidents">{r.route}</Link></td>
                      <td>{r.requests.toLocaleString()}</td>
                      <td className={r.errorRate > 0.05 ? 'bad' : ''}>{pct(r.errorRate)}</td>
                      <td>{Math.round(r.avgLatencyMs)} ms</td><td>{r.maxLatencyMs.toLocaleString()} ms</td>
                      <td>{r.rateLimited}</td><td>{r.authFailures}</td><td>{r.timeouts}</td>
                      <td><div className="bar" aria-label={`5xx share ${(r.errorRate * 100).toFixed(1)} percent`}><div style={{ width: `${Math.min(100, r.errorRate * 100)}%` }} /></div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </StateView>
    </>
  );
}
