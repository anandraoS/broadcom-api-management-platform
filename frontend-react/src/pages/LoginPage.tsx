import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../core/auth';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!username.trim() || !password) return;
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
      navigate('/dashboard');
    } catch {
      // one generic message for every failure: no hint about which field was wrong
      setError('Sign-in failed. Check your credentials and try again.');
      setBusy(false);
    }
  }

  return (
    <main className="login-wrap">
      <div className="login-card">
        <h1 className="page-title">Sign in</h1>
        <p className="muted">Development identity mode. Credentials come from the local dev identity provider; production uses your OIDC provider.</p>
        <form className="stack" onSubmit={submit} noValidate>
          <label className="field">Username
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" maxLength={64}
                   aria-invalid={touched && !username.trim()} required />
            {touched && !username.trim() && <span className="field-error">Username is required</span>}
          </label>
          <label className="field">Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" maxLength={128}
                   aria-invalid={touched && !password} required />
            {touched && !password && <span className="field-error">Password is required</span>}
          </label>
          {error && <div className="error" role="alert">{error}</div>}
          <button className="primary" type="submit" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
        </form>
      </div>
    </main>
  );
}
