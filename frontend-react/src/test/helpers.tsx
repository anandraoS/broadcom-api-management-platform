import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../core/auth';

/** Unsigned JWT-shaped token: the UI never verifies signatures (the server does). */
export function fakeToken(o: { sub?: string; roles?: string[]; team?: string; expInSeconds?: number } = {}): string {
  const b64 = (x: object) => btoa(JSON.stringify(x)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const exp = Math.floor(Date.now() / 1000) + (o.expInSeconds ?? 3600);
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: o.sub ?? 'tester', roles: o.roles ?? ['VIEWER'], team: o.team ?? 'platform', exp })}.sig`;
}

export function signIn(o: Parameters<typeof fakeToken>[0] = {}): void {
  sessionStorage.setItem('apiplatform.session', fakeToken(o));
}

export function renderApp(ui: ReactElement, route = '/') {
  return render(<MemoryRouter initialEntries={[route]}><AuthProvider>{ui}</AuthProvider></MemoryRouter>);
}

/** Replaces fetch with a handler; returns the mock so tests can assert on calls. */
export function mockFetch(handler: (url: string, init: RequestInit) => { status?: number; body?: unknown }) {
  const fn = vi.fn(async (url: string | URL | Request, init: RequestInit = {}) => {
    const r = handler(String(url), init);
    return new Response(r.body === undefined ? null : JSON.stringify(r.body), { status: r.status ?? 200, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}
