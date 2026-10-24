import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { Chip, fmtTs, StateView } from '../components/ui';
import { api, ApiError, describeError } from '../core/api';
import { useAuth } from '../core/auth';
import type { Api, Lifecycle, Policy, PolicyConfig, ValidationResult } from '../core/models';
import { useAsync } from '../core/useAsync';

const NEXT: Record<Lifecycle, Lifecycle[]> = { DRAFT: ['ACTIVE', 'INACTIVE'], ACTIVE: ['INACTIVE', 'DEPRECATED'], INACTIVE: ['ACTIVE'], DEPRECATED: ['INACTIVE'] };
const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];
type Tab = 'overview' | 'versions' | 'policies';

export default function ApiDetailPage() {
  const id = Number(useParams().id);
  const { user } = useAuth();
  const state = useAsync((s) => api.getApi(id, s), [id]);
  const [tab, setTab] = useState<Tab>('overview');
  const a = state.data;
  const canModify = !!a && !!user && (user.roles.includes('ADMIN') || (user.roles.includes('API_OWNER') && user.team === a.ownerTeam));

  return (
    <StateView loading={state.loading && !a} error={state.error} empty={!a}>
      {a && (
        <>
          <h1 className="page-title">{a.name} <Chip value={a.lifecycle} /></h1>
          <p className="muted"><code>{a.slug}</code> - owned by team <strong>{a.ownerTeam}</strong> - record version {a.version}</p>
          {!canModify && <p className="muted" role="note">Read-only: you are not an administrator or an owner of team {a.ownerTeam}. The server enforces this too.</p>}
          <div className="tabs" role="tablist" aria-label="API sections">
            {([['overview', 'Overview'], ['versions', 'Versions & targets'], ['policies', 'Policies']] as [Tab, string][]).map(([k, label]) => (
              <button key={k} role="tab" type="button" aria-selected={tab === k} onClick={() => setTab(k)}>{label}</button>
            ))}
          </div>
          {tab === 'overview' && <Overview a={a} canModify={canModify} onChanged={state.reload} />}
          {tab === 'versions' && <Versions a={a} canModify={canModify} onChanged={state.reload} />}
          {tab === 'policies' && <Policies a={a} canModify={canModify} />}
        </>
      )}
    </StateView>
  );
}

function useNotice() {
  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const el = notice && <div className={notice.ok ? 'empty' : 'error'} role={notice.ok ? 'status' : 'alert'}>{notice.text}</div>;
  return { setNotice, el };
}

function Overview({ a, canModify, onChanged }: { a: Api; canModify: boolean; onChanged: () => void }) {
  const [name, setName] = useState(a.name);
  const [description, setDescription] = useState(a.description ?? '');
  const [busy, setBusy] = useState(false);
  const { setNotice, el } = useNotice();
  useEffect(() => { setName(a.name); setDescription(a.description ?? ''); }, [a]);

  async function run(fn: () => Promise<unknown>, ok: string) {
    setBusy(true);
    try { await fn(); setNotice({ ok: true, text: ok }); onChanged(); } catch (e) { setNotice({ ok: false, text: describeError(e) }); }
    setBusy(false);
  }

  return (
    <div>
      <form className="stack" aria-label="Edit API" noValidate onSubmit={(e: FormEvent) => { e.preventDefault(); if (name.trim()) void run(() => api.updateApi(a.id, { name, description, version: a.version }), 'Saved'); }}>
        <label className="field">Name<input value={name} onChange={(e) => setName(e.target.value)} aria-invalid={!name.trim()} />
          {!name.trim() && <span className="field-error">Name is required</span>}</label>
        <label className="field">Description<textarea rows={3} maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
        {canModify && <div><button className="primary" type="submit" disabled={busy}>Save</button></div>}
      </form>
      {canModify && (
        <>
          <h3>Lifecycle</h3>
          <div className="toolbar">
            {NEXT[a.lifecycle].map((t) => <button key={t} type="button" className="secondary" disabled={busy} onClick={() => void run(() => api.transition(a.id, t), `Moved to ${t}`)}>Move to {t}</button>)}
          </div>
          <p className="muted">An API is published to gateways only when it is ACTIVE, has an ACTIVE policy and at least one valid target.</p>
        </>
      )}
      {el}
    </div>
  );
}

function Versions({ a, canModify, onChanged }: { a: Api; canModify: boolean; onChanged: () => void }) {
  const [target, setTarget] = useState({ url: '', weight: 1 });
  const [version, setVersion] = useState({ label: '', basePath: '' });
  const [touchedT, setTouchedT] = useState(false);
  const [touchedV, setTouchedV] = useState(false);
  const [busy, setBusy] = useState(false);
  const [targetErrors, setTargetErrors] = useState<string[]>([]);
  const { setNotice, el } = useNotice();
  const urlOk = /^https?:\/\/\S+$/.test(target.url) && target.weight >= 1 && target.weight <= 100;
  const labelOk = /^[A-Za-z0-9._-]{1,20}$/.test(version.label);
  const pathOk = /^\/[a-z0-9][a-z0-9/_-]{0,100}$/.test(version.basePath);

  async function addTarget(e: FormEvent, versionId: number) {
    e.preventDefault();
    setTouchedT(true);
    if (!urlOk) return;
    setTargetErrors([]);
    setBusy(true);
    try { await api.addTarget(a.id, versionId, target); setTarget({ url: '', weight: 1 }); setTouchedT(false); onChanged(); }
    catch (err) {
      if (err instanceof ApiError && err.details.length) setTargetErrors(err.details); else setNotice({ ok: false, text: describeError(err) });
    }
    setBusy(false);
  }
  async function simple(fn: () => Promise<unknown>, ok: string, after?: () => void) {
    setBusy(true);
    try { await fn(); setNotice({ ok: true, text: ok }); after?.(); onChanged(); } catch (err) { setNotice({ ok: false, text: describeError(err) }); }
    setBusy(false);
  }

  return (
    <div>
      {a.versions.map((v) => (
        <section key={v.id} aria-label={`Version ${v.label}`}>
          <h3><code>{v.label}</code> <Chip value={v.status} /> <span className="muted">gateway path: /gw{v.basePath}</span></h3>
          {!v.targets.length ? <div className="empty">No backend targets yet - this version is not routable.</div> : (
            <div className="table-wrap">
              <table aria-label={`Targets of ${v.label}`}>
                <thead><tr><th>URL</th><th>Weight</th><th>Health path</th><th /></tr></thead>
                <tbody>{v.targets.map((t) => (
                  <tr key={t.id}><td><code>{t.url}</code></td><td>{t.weight}</td><td><code>{t.healthPath}</code></td>
                    <td>{canModify && <button type="button" onClick={() => void simple(() => api.removeTarget(a.id, v.id, t.id), 'Target removed')}>Remove</button>}</td></tr>
                ))}</tbody>
              </table>
            </div>
          )}
          {canModify && (
            <form className="grid2" noValidate aria-label={`Add target to ${v.label}`} onSubmit={(e) => void addTarget(e, v.id)}>
              <label className="field">Backend URL
                <input value={target.url} placeholder="http://orders-service-1:9001" onChange={(e) => setTarget({ ...target, url: e.target.value })} aria-invalid={touchedT && !urlOk} />
                {touchedT && !urlOk && <span className="field-error">Enter an http(s) URL and weight 1-100</span>}
              </label>
              <label className="field">Weight (1-100)
                <input type="number" value={target.weight} onChange={(e) => setTarget({ ...target, weight: Number(e.target.value) })} />
              </label>
              <div><button type="submit" className="secondary" disabled={busy}>Add target</button></div>
            </form>
          )}
        </section>
      ))}
      {targetErrors.length > 0 && (
        <div className="error" role="alert"><strong>Target rejected by destination policy (SSRF protection):</strong>
          <ul>{targetErrors.map((e) => <li key={e}>{e}</li>)}</ul></div>
      )}
      {canModify && (
        <>
          <h3>Add version</h3>
          <form className="grid2" noValidate aria-label="Add version" onSubmit={(e) => { e.preventDefault(); setTouchedV(true); if (labelOk && pathOk) void simple(() => api.addVersion(a.id, version), 'Version added', () => { setVersion({ label: '', basePath: '' }); setTouchedV(false); }); }}>
            <label className="field">Label<input value={version.label} placeholder="v2" onChange={(e) => setVersion({ ...version, label: e.target.value })} aria-invalid={touchedV && !labelOk} />
              {touchedV && !labelOk && <span className="field-error">1-20 chars: letters, digits, . _ -</span>}</label>
            <label className="field">Base path<input value={version.basePath} placeholder="/orders/v2" onChange={(e) => setVersion({ ...version, basePath: e.target.value })} aria-invalid={touchedV && !pathOk} />
              {touchedV && !pathOk && <span className="field-error">Lowercase path starting with /</span>}</label>
            <div><button type="submit" className="secondary" disabled={busy}>Add version</button></div>
          </form>
        </>
      )}
      {el}
    </div>
  );
}

const DEFAULT_FORM = { auth: 'API_KEY' as PolicyConfig['auth'], requiredRole: '', allowedMethods: ['GET'], rateLimitPerMinute: 100, burst: 20,
  rateAlgorithm: 'TOKEN_BUCKET' as PolicyConfig['rateAlgorithm'], timeoutMs: 3000, maxRequestBytes: 65536, ipAllowCidrs: '', retryEnabled: true, comment: '' };

export function buildConfig(f: typeof DEFAULT_FORM): PolicyConfig {
  const cidrs = f.ipAllowCidrs.split(',').map((s) => s.trim()).filter(Boolean);
  return { auth: f.auth, requiredRole: f.requiredRole.trim() || null, allowedMethods: f.allowedMethods, rateLimitPerMinute: Number(f.rateLimitPerMinute),
    burst: Number(f.burst), rateAlgorithm: f.rateAlgorithm, timeoutMs: Number(f.timeoutMs), maxRequestBytes: Number(f.maxRequestBytes),
    ipAllowCidrs: cidrs.length ? cidrs : null, retryEnabled: f.retryEnabled };
}

export function formErrors(f: typeof DEFAULT_FORM): Record<string, string> {
  const e: Record<string, string> = {};
  if (!f.allowedMethods.length) e.allowedMethods = 'Select at least one method';
  if (!(f.rateLimitPerMinute >= 1 && f.rateLimitPerMinute <= 6_000_000)) e.rateLimitPerMinute = '1 - 6,000,000';
  if (!(f.burst >= 1)) e.burst = 'Burst must be >= 1';
  if (!(f.timeoutMs >= 100 && f.timeoutMs <= 60_000)) e.timeoutMs = '100 - 60000';
  if (!(f.maxRequestBytes >= 0)) e.maxRequestBytes = 'Must be >= 0';
  return e;
}

function Policies({ a, canModify }: { a: Api; canModify: boolean }) {
  const { hasAnyRole } = useAuth();
  const history = useAsync((s) => api.policyHistory(a.id, s), [a.id]);
  const [form, setForm] = useState(DEFAULT_FORM);
  const [touched, setTouched] = useState(false);
  const [validation, setValidation] = useState<ValidationResult | null>(null);
  const [busy, setBusy] = useState(false);
  const { setNotice, el } = useNotice();
  const isApprover = hasAnyRole('ADMIN', 'POLICY_APPROVER');
  const errs = formErrors(form);

  async function run(fn: () => Promise<unknown>, ok: string) {
    setBusy(true);
    try { await fn(); setNotice({ ok: true, text: ok }); history.reload(); } catch (e) { setNotice({ ok: false, text: describeError(e) }); }
    setBusy(false);
  }

  return (
    <div>
      <StateView loading={history.loading} error={history.error} empty={!history.data?.length}
                 emptyText="No policy has been submitted yet - this API will not be routable until one is approved.">
        <div className="table-wrap">
          <table aria-label="Policy history">
            <thead><tr><th>Version</th><th>Status</th><th>Auth</th><th>Rate</th><th>Timeout</th><th>Submitted by</th><th>Decided by</th><th>Comment</th><th /></tr></thead>
            <tbody>
              {history.data?.map((p: Policy) => (
                <tr key={p.id}>
                  <td>v{p.versionNo}</td><td><Chip value={p.status} /></td><td>{p.config.auth}</td>
                  <td>{p.config.rateLimitPerMinute}/min {p.config.rateAlgorithm}</td><td>{p.config.timeoutMs} ms</td>
                  <td>{p.submittedBy}</td><td>{p.approvedBy ?? '-'}</td><td>{p.comment}</td>
                  <td>
                    {p.status === 'PENDING_APPROVAL' && isApprover && <>
                      <button type="button" disabled={busy} onClick={() => void run(() => api.approve(p.id), 'Approved and activated')}>Approve</button>{' '}
                      <button type="button" disabled={busy} onClick={() => void run(() => api.reject(p.id), 'Rejected')}>Reject</button></>}
                    {(p.status === 'ACTIVE' || p.status === 'SUPERSEDED') && isApprover &&
                      <button type="button" disabled={busy} onClick={() => { if (confirm(`Create a new active version from v${p.versionNo}? Gateways pick it up on their next poll.`)) void run(() => api.revert(a.id, p.versionNo), 'Reverted'); }}>Revert to v{p.versionNo}</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </StateView>

      {canModify && (
        <>
          <h3>New policy version</h3>
          <p className="muted">Changes are validated, then need approval from a different user with the POLICY_APPROVER role before gateways receive them. Propagation is by polling (seconds), not instantaneous.</p>
          <form className="grid2" noValidate aria-label="Policy editor" onSubmit={(e) => {
            e.preventDefault(); setTouched(true);
            if (Object.keys(errs).length) return;
            setValidation(null);
            void run(() => api.submitPolicy(a.id, buildConfig(form), form.comment), 'Submitted for approval');
          }}>
            <label className="field">Authentication
              <select value={form.auth} onChange={(e) => setForm({ ...form, auth: e.target.value as PolicyConfig['auth'] })}>
                <option value="API_KEY">API key</option><option value="JWT">JWT (OIDC)</option><option value="NONE">None (read-only methods only)</option>
              </select></label>
            <label className="field">Required role (JWT roles claim)<input value={form.requiredRole} onChange={(e) => setForm({ ...form, requiredRole: e.target.value })} /></label>
            <fieldset className="field"><legend>Allowed methods</legend>
              {METHODS.map((m) => (
                <label key={m} style={{ marginRight: 8 }}><input type="checkbox" style={{ minWidth: 0 }} checked={form.allowedMethods.includes(m)}
                  onChange={(e) => setForm({ ...form, allowedMethods: e.target.checked ? [...form.allowedMethods, m] : form.allowedMethods.filter((x) => x !== m) })} /> {m}</label>
              ))}
              {touched && errs.allowedMethods && <span className="field-error">{errs.allowedMethods}</span>}
            </fieldset>
            <label className="field">Rate limit per minute<input type="number" value={form.rateLimitPerMinute} onChange={(e) => setForm({ ...form, rateLimitPerMinute: Number(e.target.value) })} aria-invalid={touched && !!errs.rateLimitPerMinute} />
              {touched && errs.rateLimitPerMinute && <span className="field-error">{errs.rateLimitPerMinute}</span>}</label>
            <label className="field">Burst<input type="number" value={form.burst} onChange={(e) => setForm({ ...form, burst: Number(e.target.value) })} aria-invalid={touched && !!errs.burst} /></label>
            <label className="field">Algorithm
              <select value={form.rateAlgorithm} onChange={(e) => setForm({ ...form, rateAlgorithm: e.target.value as PolicyConfig['rateAlgorithm'] })}>
                <option value="TOKEN_BUCKET">Token bucket</option><option value="SLIDING_WINDOW">Sliding window</option><option value="FIXED_WINDOW">Fixed window</option>
              </select></label>
            <label className="field">End-to-end timeout (ms)<input type="number" value={form.timeoutMs} onChange={(e) => setForm({ ...form, timeoutMs: Number(e.target.value) })} aria-invalid={touched && !!errs.timeoutMs} />
              {touched && errs.timeoutMs && <span className="field-error">{errs.timeoutMs}</span>}</label>
            <label className="field">Max request bytes<input type="number" value={form.maxRequestBytes} onChange={(e) => setForm({ ...form, maxRequestBytes: Number(e.target.value) })} /></label>
            <label className="field">IP allowlist (CIDRs, comma separated)<input value={form.ipAllowCidrs} placeholder="10.0.0.0/8, 192.168.1.0/24" onChange={(e) => setForm({ ...form, ipAllowCidrs: e.target.value })} /></label>
            <label className="field">Comment for reviewer<input value={form.comment} maxLength={500} onChange={(e) => setForm({ ...form, comment: e.target.value })} /></label>
            <label><input type="checkbox" style={{ minWidth: 0 }} checked={form.retryEnabled} onChange={(e) => setForm({ ...form, retryEnabled: e.target.checked })} /> Retry safe methods (GET/HEAD/OPTIONS) on transient failures</label>
            <div className="toolbar">
              <button type="button" className="secondary" disabled={busy} onClick={() => { api.validatePolicy(buildConfig(form)).then(setValidation).catch((e) => setNotice({ ok: false, text: describeError(e) })); }}>Validate (dry run)</button>
              <button type="submit" className="primary" disabled={busy}>Submit for approval</button>
            </div>
          </form>
          {validation && (validation.valid
            ? <div className="empty" role="status">Policy is valid.</div>
            : <div className="error" role="alert"><ul>{validation.errors.map((e) => <li key={e}>{e}</li>)}</ul></div>)}
        </>
      )}
      {el}
      <p className="muted">History shows {history.data?.length ?? 0} versions; last change {history.data?.[0] ? fmtTs(history.data[0].createdAt) : '-'}.</p>
    </div>
  );
}
