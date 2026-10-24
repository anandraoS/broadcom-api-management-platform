import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Chip, StateView } from '../components/ui';
import { api, describeError } from '../core/api';
import { useAuth } from '../core/auth';
import type { Api as ApiModel, KeyIssued } from '../core/models';
import { useAsync } from '../core/useAsync';

export default function ConsumerDetailPage() {
  const id = Number(useParams().id);
  const { hasAnyRole } = useAuth();
  const canManage = hasAnyRole('ADMIN', 'API_OWNER');
  const subs = useAsync((s) => api.subscriptions(id, s), [id]);
  const keys = useAsync((s) => (canManage ? api.keys(id, s) : Promise.resolve([])), [id, canManage]);
  const [apis, setApis] = useState<ApiModel[]>([]);
  const [apiId, setApiId] = useState(0);
  const [days, setDays] = useState(90);
  const [issued, setIssued] = useState<KeyIssued | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  async function act(fn: () => Promise<unknown>, ok: string) {
    try { await fn(); setMsg({ ok: true, text: ok }); subs.reload(); keys.reload(); } catch (e) { setMsg({ ok: false, text: describeError(e) }); }
  }
  async function loadApis() {
    if (apis.length) return;
    try { setApis((await api.searchApis({ size: 100 })).content); } catch (e) { setMsg({ ok: false, text: describeError(e) }); }
  }
  const daysOk = days >= 1 && days <= 365;

  return (
    <>
      <h1 className="page-title">Consumer #{id}</h1>
      <h2>Subscriptions</h2>
      <StateView loading={subs.loading} error={subs.error} empty={!subs.data?.length} emptyText="Not subscribed to any API. A key only works for APIs the consumer is subscribed to.">
        <div className="table-wrap">
          <table aria-label="Subscriptions">
            <thead><tr><th>API</th><th>Status</th><th /></tr></thead>
            <tbody>{subs.data?.map((s) => (
              <tr key={s.id}><td><code>{s.apiSlug}</code></td><td><Chip value={s.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'} /></td>
                <td>{canManage && s.status === 'ACTIVE' && <button type="button" onClick={() => void act(() => api.cancelSubscription(id, s.id), 'Subscription cancelled')}>Cancel</button>}</td></tr>
            ))}</tbody>
          </table>
        </div>
      </StateView>
      {canManage && (
        <form className="toolbar" aria-label="Subscribe to an API" onSubmit={(e) => { e.preventDefault(); if (apiId) void act(() => api.subscribe(id, apiId), 'Subscribed'); }}>
          <label className="field">API
            <select value={apiId} onFocus={() => void loadApis()} onChange={(e) => setApiId(Number(e.target.value))}>
              <option value={0}>Select...</option>
              {apis.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.slug})</option>)}
            </select></label>
          <button type="submit" className="secondary" disabled={!apiId}>Subscribe</button>
        </form>
      )}

      <h2>API keys</h2>
      {!canManage ? <p className="muted">Only administrators and API owners can manage keys.</p> : (
        <>
          {issued && (
            <div className="secret" role="alert">
              <strong>Copy this key now - it is shown once and only a hash is stored.</strong>
              <pre className="mono" data-testid="issued-key">{issued.apiKey}</pre>
              <button type="button" className="secondary" onClick={() => setIssued(null)}>I have stored it</button>
            </div>
          )}
          <form className="toolbar" aria-label="Issue API key" onSubmit={(e) => { e.preventDefault(); if (daysOk) void act(async () => setIssued(await api.issueKey(id, days)), 'Key issued'); }}>
            <label className="field">Valid for (days, max 365)<input type="number" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-invalid={!daysOk} /></label>
            <button type="submit" className="primary" disabled={!daysOk}>Issue key</button>
          </form>
          <StateView loading={keys.loading} error={keys.error} empty={!keys.data?.length} emptyText="No keys issued.">
            <div className="table-wrap">
              <table aria-label="API keys">
                <thead><tr><th>Prefix</th><th>Expires</th><th>Status</th><th /></tr></thead>
                <tbody>{keys.data?.map((k) => (
                  <tr key={k.id}><td><code>{k.keyPrefix}</code></td><td>{k.expiresAt.slice(0, 10)}</td><td><Chip value={k.revoked ? 'REJECTED' : 'ACTIVE'} /></td>
                    <td>{!k.revoked && <button type="button" onClick={() => { if (confirm(`Revoke key ${k.keyPrefix}? Calls using it fail within one gateway poll interval.`)) void act(() => api.revokeKey(id, k.id), 'Key revoked'); }}>Revoke</button>}</td></tr>
                ))}</tbody>
              </table>
            </div>
          </StateView>
          <p className="muted">Rotate by issuing a new key, switching the consumer over, then revoking the old one.</p>
        </>
      )}
      {msg && <div className={msg.ok ? 'empty' : 'error'} role={msg.ok ? 'status' : 'alert'}>{msg.text}</div>}
    </>
  );
}
