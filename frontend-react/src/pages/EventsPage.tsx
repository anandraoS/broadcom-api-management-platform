import { useState } from 'react';
import { Chip, fmtTs, Pager, StateView } from '../components/ui';
import { api } from '../core/api';
import { useAsync, useDebounced } from '../core/useAsync';

const TYPES = ['CIRCUIT_OPEN', 'TARGET_UNHEALTHY', 'TARGET_RECOVERED', 'SNAPSHOT_STALE', 'INVALID_CONFIG', 'RATE_LIMITER_DEGRADED', 'RATE_LIMITER_RECOVERED', 'POOL_EXHAUSTED', 'QUEUE_BACKLOG'];

export default function EventsPage() {
  const [type, setType] = useState('');
  const [severity, setSeverity] = useState('');
  const [route, setRoute] = useState('');
  const [page, setPage] = useState(0);
  const droute = useDebounced(route, 300);
  const list = useAsync((s) => api.events({ type, severity, route: droute, page, size: 25 }, s), [type, severity, droute, page]);
  const d = list.data;
  return (
    <>
      <h1 className="page-title">Operational events</h1>
      <form className="toolbar" role="search" aria-label="Filter events" onSubmit={(e) => e.preventDefault()}>
        <label className="field">Type
          <select value={type} onChange={(e) => { setType(e.target.value); setPage(0); }}><option value="">All</option>{TYPES.map((t) => <option key={t} value={t}>{t}</option>)}</select></label>
        <label className="field">Severity
          <select value={severity} onChange={(e) => { setSeverity(e.target.value); setPage(0); }}><option value="">All</option><option>INFO</option><option>WARN</option><option>ERROR</option></select></label>
        <label className="field">Route (api/version)<input value={route} maxLength={80} onChange={(e) => { setRoute(e.target.value); setPage(0); }} /></label>
      </form>
      <StateView loading={list.loading} error={list.error} empty={!d?.content.length} emptyText="No events match these filters.">
        {d && (
          <>
            <div className="table-wrap">
              <table aria-label="Events">
                <thead><tr><th>Time</th><th>Severity</th><th>Type</th><th>Route</th><th>Message</th><th>Correlation id</th></tr></thead>
                <tbody>{d.content.map((e) => (
                  <tr key={e.id}><td>{fmtTs(e.ts)}</td><td><Chip value={e.severity} /></td><td>{e.type}</td><td>{e.routeSlug ?? '-'}</td><td>{e.message}</td><td><code>{e.correlationId ?? '-'}</code></td></tr>
                ))}</tbody>
              </table>
            </div>
            <Pager page={d.page} size={d.size} total={d.totalElements} onPage={setPage} label="Select page of events" />
          </>
        )}
      </StateView>
    </>
  );
}
