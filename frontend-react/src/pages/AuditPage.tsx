import { useState } from 'react';
import { fmtTs, Pager, StateView } from '../components/ui';
import { api } from '../core/api';
import { useAsync, useDebounced } from '../core/useAsync';

/** Append-only history of administrative actions. Detail text is rendered as text (React escapes it; no dangerouslySetInnerHTML). */
export default function AuditPage() {
  const [entityType, setEntityType] = useState('');
  const [entityId, setEntityId] = useState('');
  const [actor, setActor] = useState('');
  const [page, setPage] = useState(0);
  const dId = useDebounced(entityId, 300);
  const dActor = useDebounced(actor, 300);
  const list = useAsync((s) => api.audit({ entityType, entityId: dId, actor: dActor, page, size: 25 }, s), [entityType, dId, dActor, page]);
  const d = list.data;
  return (
    <>
      <h1 className="page-title">Audit history</h1>
      <form className="toolbar" role="search" aria-label="Filter audit events" onSubmit={(e) => e.preventDefault()}>
        <label className="field">Entity type
          <select value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}><option value="">All</option><option>API</option><option>CONSUMER</option></select></label>
        <label className="field">Entity id<input value={entityId} maxLength={40} onChange={(e) => { setEntityId(e.target.value); setPage(0); }} /></label>
        <label className="field">Actor<input value={actor} maxLength={80} onChange={(e) => { setActor(e.target.value); setPage(0); }} /></label>
      </form>
      <StateView loading={list.loading} error={list.error} empty={!d?.content.length} emptyText="No audit events match.">
        {d && (
          <>
            <div className="table-wrap">
              <table aria-label="Audit events">
                <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Detail</th><th>Correlation id</th></tr></thead>
                <tbody>{d.content.map((a) => (
                  <tr key={a.id}><td>{fmtTs(a.ts)}</td><td>{a.actor}</td><td>{a.action}</td><td>{a.entityType} {a.entityId}</td><td>{a.detail}</td><td><code>{a.correlationId ?? '-'}</code></td></tr>
                ))}</tbody>
              </table>
            </div>
            <Pager page={d.page} size={d.size} total={d.totalElements} onPage={setPage} label="Select page of audit events" />
          </>
        )}
      </StateView>
    </>
  );
}
