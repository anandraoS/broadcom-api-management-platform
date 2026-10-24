import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Chip, Pager, StateView } from '../components/ui';
import { api, describeError } from '../core/api';
import { useAuth } from '../core/auth';
import { useAsync, useDebounced } from '../core/useAsync';

const CLIENT = /^[a-z0-9][a-z0-9-]{2,78}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ConsumerListPage() {
  const { hasAnyRole } = useAuth();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const dq = useDebounced(q, 300);
  const list = useAsync((s) => api.searchConsumers({ q: dq, page, size }, s), [dq, page, size]);
  const [show, setShow] = useState(false);
  const [form, setForm] = useState({ name: '', clientId: '', ownerEmail: '' });
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const valid = form.name.trim() !== '' && CLIENT.test(form.clientId) && EMAIL.test(form.ownerEmail);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!valid) return;
    try {
      await api.createConsumer(form);
      setForm({ name: '', clientId: '', ownerEmail: '' }); setTouched(false); setShow(false); setError(null); list.reload();
    } catch (err) { setError(describeError(err)); }
  }

  const d = list.data;
  return (
    <>
      <h1 className="page-title">Consumer applications</h1>
      <div className="toolbar">
        <label className="field">Search by name<input value={q} maxLength={80} onChange={(e) => { setQ(e.target.value); setPage(0); }} /></label>
        <span className="spacer" />
        {hasAnyRole('ADMIN', 'API_OWNER') && <button type="button" className="primary" onClick={() => setShow((s) => !s)}>{show ? 'Close' : 'Register consumer'}</button>}
      </div>
      {show && (
        <form className="grid2" noValidate aria-label="Register consumer" onSubmit={submit}>
          <label className="field">Name<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} aria-invalid={touched && !form.name.trim()} />
            {touched && !form.name.trim() && <span className="field-error">Name is required</span>}</label>
          <label className="field">Client id<input value={form.clientId} placeholder="acme-partner" onChange={(e) => setForm({ ...form, clientId: e.target.value })} aria-invalid={touched && !CLIENT.test(form.clientId)} />
            {touched && !CLIENT.test(form.clientId) && <span className="field-error">3-79 chars: lowercase letters, digits, dashes</span>}</label>
          <label className="field">Owner email<input type="email" value={form.ownerEmail} onChange={(e) => setForm({ ...form, ownerEmail: e.target.value })} aria-invalid={touched && !EMAIL.test(form.ownerEmail)} />
            {touched && !EMAIL.test(form.ownerEmail) && <span className="field-error">Valid email required</span>}</label>
          <div><button type="submit" className="primary">Create</button></div>
          {error && <div className="error" role="alert">{error}</div>}
        </form>
      )}
      <StateView loading={list.loading} error={list.error} empty={!d?.content.length} emptyText="No consumers found.">
        {d && (
          <>
            <div className="table-wrap">
              <table aria-label="Consumers">
                <thead><tr><th>Name</th><th>Client id</th><th>Owner</th><th>Status</th></tr></thead>
                <tbody>{d.content.map((c) => (
                  <tr key={c.id}><td><Link to={`/consumers/${c.id}`}>{c.name}</Link></td><td><code>{c.clientId}</code></td><td>{c.ownerEmail}</td>
                    <td><Chip value={c.active ? 'ACTIVE' : 'INACTIVE'} /></td></tr>
                ))}</tbody>
              </table>
            </div>
            <Pager page={d.page} size={d.size} total={d.totalElements} onPage={setPage} label="Select page of consumers" />
          </>
        )}
      </StateView>
    </>
  );
}
