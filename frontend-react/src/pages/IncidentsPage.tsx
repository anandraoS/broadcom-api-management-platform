import { useState } from 'react';
import { Chip, fmtTs, StateView } from '../components/ui';
import { api } from '../core/api';
import { useAsync } from '../core/useAsync';

/**
 * Investigation view. Findings are produced by deterministic threshold rules on the server and always show the evidence
 * that triggered them. Nothing on this page is AI-generated.
 */
export default function IncidentsPage() {
  const [windowMin, setWindowMin] = useState(30);
  const rep = useAsync((s) => api.incidents(windowMin, s), [windowMin]);
  const r = rep.data;
  return (
    <>
      <h1 className="page-title">Incident investigation</h1>
      <div className="toolbar">
        <label className="field">Look-back window
          <select value={windowMin} onChange={(e) => setWindowMin(Number(e.target.value))}>
            {[15, 30, 60, 240, 1440].map((w) => <option key={w} value={w}>Last {w} min</option>)}
          </select></label>
      </div>
      <StateView loading={rep.loading && !r} error={rep.error}>
        {r && (
          <>
            <p className="muted" role="note">{r.note}</p>
            <h2>Findings ({r.findings.length})</h2>
            {!r.findings.length && <div className="empty">No rule fired in this window - telemetry looks healthy.</div>}
            <div className="cards">
              {r.findings.map((f) => (
                <section className="card" key={f.rule + f.route} aria-label={f.rule}>
                  <h3><Chip value={f.severity} /> {f.rule} {f.route && <small>on {f.route}</small>}</h3>
                  <p>{f.summary}</p>
                  <h4>Evidence (measured)</h4>
                  <pre className="mono">{JSON.stringify(f.evidence, null, 2)}</pre>
                  <h4>Suggested investigation steps (rule-based)</h4>
                  <ol>{f.suggestedSteps.map((s) => <li key={s}>{s}</li>)}</ol>
                </section>
              ))}
            </div>
            <h2>Recent configuration changes (possible cause)</h2>
            {!r.recentConfigChanges.length ? <div className="empty">No configuration changes in this window.</div> : (
              <div className="table-wrap"><table aria-label="Recent changes">
                <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Detail</th></tr></thead>
                <tbody>{r.recentConfigChanges.map((a) => <tr key={a.id}><td>{fmtTs(a.ts)}</td><td>{a.actor}</td><td>{a.action}</td><td>{a.entityType} {a.entityId}</td><td>{a.detail}</td></tr>)}</tbody>
              </table></div>
            )}
            <h2>Correlated events</h2>
            {!r.recentEvents.length ? <div className="empty">No operational events in this window.</div> : (
              <div className="table-wrap"><table aria-label="Recent events">
                <thead><tr><th>Time</th><th>Severity</th><th>Type</th><th>Route</th><th>Message</th></tr></thead>
                <tbody>{r.recentEvents.map((e) => <tr key={e.id}><td>{fmtTs(e.ts)}</td><td><Chip value={e.severity} /></td><td>{e.type}</td><td>{e.routeSlug ?? '-'}</td><td>{e.message}</td></tr>)}</tbody>
              </table></div>
            )}
            <p className="muted">Next: open the Grafana gateway dashboard for percentiles, and Jaeger with the correlation id from a failing request for the trace.</p>
          </>
        )}
      </StateView>
    </>
  );
}
