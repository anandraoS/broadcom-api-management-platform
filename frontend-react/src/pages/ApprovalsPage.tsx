import { useState } from 'react';
import { Link } from 'react-router-dom';
import { fmtTs, StateView } from '../components/ui';
import { api, describeError } from '../core/api';
import { useAuth } from '../core/auth';
import { useAsync } from '../core/useAsync';

/** Four-eyes review queue: a policy cannot be approved by the person who submitted it (enforced by the server). */
export default function ApprovalsPage() {
  const { user } = useAuth();
  const pending = useAsync((s) => api.pendingPolicies(s), []);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  async function decide(id: number, approve: boolean) {
    try {
      await (approve ? api.approve(id) : api.reject(id));
      setMessage({ ok: true, text: approve ? 'Approved and activated' : 'Rejected' });
      pending.reload();
    } catch (e) {
      setMessage({ ok: false, text: describeError(e) });
    }
  }

  return (
    <>
      <h1 className="page-title">Policy approvals</h1>
      {message && <div className={message.ok ? 'empty' : 'error'} role={message.ok ? 'status' : 'alert'}>{message.text}</div>}
      <StateView loading={pending.loading} error={pending.error} empty={!pending.data?.length} emptyText="No policy changes are waiting for review.">
        <div className="cards">
          {pending.data?.map((p) => (
            <section className="card" key={p.id} aria-label={`Policy ${p.id}`}>
              <h3><Link to={`/apis/${p.apiId}`}>API #{p.apiId}</Link> - policy v{p.versionNo}</h3>
              <p className="muted">Submitted by <strong>{p.submittedBy}</strong> at {fmtTs(p.createdAt)}</p>
              <p>{p.comment || 'No comment'}</p>
              <ul>
                <li>Auth: {p.config.auth}</li>
                <li>Methods: {p.config.allowedMethods.join(', ')}</li>
                <li>Rate: {p.config.rateLimitPerMinute}/min ({p.config.rateAlgorithm}, burst {p.config.burst})</li>
                <li>Timeout: {p.config.timeoutMs} ms - max body {p.config.maxRequestBytes} B - retries {p.config.retryEnabled ? 'on' : 'off'}</li>
                <li>IP allowlist: {p.config.ipAllowCidrs?.join(', ') ?? 'none'}</li>
              </ul>
              {p.submittedBy === user?.username
                ? <p className="muted" role="note">You submitted this change; another approver must review it.</p>
                : <><button className="primary" type="button" onClick={() => decide(p.id, true)}>Approve</button>{' '}
                    <button className="secondary" type="button" onClick={() => decide(p.id, false)}>Reject</button></>}
            </section>
          ))}
        </div>
      </StateView>
    </>
  );
}
