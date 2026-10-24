import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Chip, fmtTs, Pager, StateView } from '../components/ui';
import { api, describeError } from '../core/api';
import { useAuth } from '../core/auth';
import { useAsync, useDebounced } from '../core/useAsync';

const LIFECYCLES = ['DRAFT', 'ACTIVE', 'INACTIVE', 'DEPRECATED'];
const SLUG = /^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$/;
const TEAM = /^[a-z0-9-]{2,80}$/;

export default function ApiListPage() {
  const { user, hasAnyRole } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [lifecycle, setLifecycle] = useState('');
  const [owner, setOwner] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const filters = useDebounced({ q, lifecycle, owner }, 300);
  const list = useAsync((s) => api.searchApis({ ...filters, page, size }, s), [filters.q, filters.lifecycle, filters.owner, page, size]);

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ slug: '', name: '', ownerTeam: user?.team ?? '', description: '' });
  const [touched, setTouched] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  // one idempotency key per form instance: a double click or retry cannot create the API twice
  const idemKey = useRef(crypto.randomUUID());
  const canCreate = hasAnyRole('ADMIN', 'API_OWNER');
  const valid = SLUG.test(form.slug) && form.name.trim() !== '' && TEAM.test(form.ownerTeam);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!valid) return;
    setCreating(true);
    setCreateError(null);
    try {
      const a = await api.createApi({ ...form, description: form.description || undefined }, idemKey.current);
      idemKey.current = crypto.randomUUID();
      navigate(`/apis/${a.id}`);
    } catch (err) {
      setCreateError(describeError(err));
      setCreating(false);
    }
  }

  const d = list.data;
  return (
    <>
      <h1 className="page-title">API registry</h1>
      <form className="toolbar" role="search" aria-label="Filter APIs" onSubmit={(e) => e.preventDefault()}>
        <label className="field">Search name or slug
          <input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} maxLength={80} />
        </label>
        <label className="field">Lifecycle
          <select value={lifecycle} onChange={(e) => { setLifecycle(e.target.value); setPage(0); }}>
            <option value="">All</option>
            {LIFECYCLES.map((l) => <option key={l} value={l}>{l}</option>)}
          </select>
        </label>
        <label className="field">Owner team
          <input value={owner} onChange={(e) => { setOwner(e.target.value); setPage(0); }} maxLength={80} />
        </label>
        <span className="spacer" />
        {canCreate && <button type="button" className="primary" onClick={() => setShowCreate((s) => !s)}>{showCreate ? 'Close' : 'Register API'}</button>}
      </form>

      {showCreate && (
        <form className="grid2" onSubmit={submit} noValidate aria-label="Register API">
          <label className="field">Slug
            <input value={form.slug} placeholder="orders" onChange={(e) => setForm({ ...form, slug: e.target.value })} aria-invalid={touched && !SLUG.test(form.slug)} />
            {touched && !SLUG.test(form.slug) && <span className="field-error">3-80 chars: lowercase letters, digits, dashes</span>}
          </label>
          <label className="field">Name
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} aria-invalid={touched && !form.name.trim()} />
            {touched && !form.name.trim() && <span className="field-error">Name is required</span>}
          </label>
          <label className="field">Owner team
            <input value={form.ownerTeam} onChange={(e) => setForm({ ...form, ownerTeam: e.target.value })} aria-invalid={touched && !TEAM.test(form.ownerTeam)} />
            {touched && !TEAM.test(form.ownerTeam) && <span className="field-error">Lowercase team id required</span>}
          </label>
          <label className="field">Description
            <input value={form.description} maxLength={1000} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </label>
          <div><button type="submit" className="primary" disabled={creating}>Create</button></div>
          {createError && <div className="error" role="alert">{createError}</div>}
        </form>
      )}

      <StateView loading={list.loading} error={list.error} empty={!d?.content.length} emptyText="No APIs match these filters.">
        {d && (
          <>
            <div className="table-wrap">
              <table aria-label="APIs">
                <thead><tr><th>Name</th><th>Slug</th><th>Owner</th><th>Lifecycle</th><th>Updated</th></tr></thead>
                <tbody>
                  {d.content.map((a) => (
                    <tr key={a.id}>
                      <td><Link to={`/apis/${a.id}`}>{a.name}</Link></td>
                      <td><code>{a.slug}</code></td><td>{a.ownerTeam}</td><td><Chip value={a.lifecycle} /></td><td>{fmtTs(a.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="toolbar">
              <label className="field">Page size
                <select value={size} onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}>
                  {[10, 20, 50].map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </label>
              <span className="spacer" />
              <Pager page={d.page} size={d.size} total={d.totalElements} onPage={setPage} label="Select page of APIs" />
            </div>
          </>
        )}
      </StateView>
    </>
  );
}
