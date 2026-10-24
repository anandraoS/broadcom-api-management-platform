import { describe, expect, it, vi } from 'vitest';
import { api, ApiError, describeError, session, setUnauthorizedHandler } from './api';
import { decodeToken } from './auth';
import { fakeToken, mockFetch } from '../test/helpers';

describe('api client', () => {
  it('sends only non-empty filters as query params', async () => {
    const f = mockFetch(() => ({ body: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }));
    await api.searchApis({ q: 'ord', lifecycle: '', owner: undefined, page: 0, size: 20 });
    const url = String(f.mock.calls[0][0]);
    expect(url).toContain('q=ord');
    expect(url).toContain('size=20');
    expect(url).not.toContain('lifecycle');
    expect(url).not.toContain('owner');
  });

  it('adds bearer token and a correlation id, but never to the login request', async () => {
    session.set(fakeToken());
    const f = mockFetch(() => ({ body: {} }));
    await api.getApi(1);
    const h = (f.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(h['Authorization']).toMatch(/^Bearer /);
    expect(h['X-Correlation-Id']).toMatch(/^[0-9a-f-]{36}$/);
    await api.login('u', 'p');
    const login = (f.mock.calls[1][1] as RequestInit).headers as Record<string, string>;
    expect(login['Authorization']).toBeUndefined();
  });

  it('sends the Idempotency-Key when creating an API', async () => {
    const f = mockFetch(() => ({ status: 201, body: { id: 1 } }));
    await api.createApi({ slug: 'orders', name: 'Orders', ownerTeam: 'orders' }, 'key-12345678');
    expect(((f.mock.calls[0][1] as RequestInit).headers as Record<string, string>)['Idempotency-Key']).toBe('key-12345678');
  });

  it('turns problem bodies into safe messages and exposes rule details', async () => {
    mockFetch(() => ({ status: 422, body: { status: 422, detail: 'Backend destination rejected', errors: ['host not in allowlist'], correlationId: 'abc-123' } }));
    const err = await api.addTarget(1, 2, { url: 'http://x', weight: 1 }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(describeError(err)).toContain('Backend destination rejected');
    expect(describeError(err)).toContain('host not in allowlist');
    expect(describeError(err)).toContain('ref: abc-123');
    expect((err as ApiError).details).toEqual(['host not in allowlist']);
  });

  it('reports network failures and unknown errors without leaking internals', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('boom <script>'); }));
    const err = await api.getApi(1).catch((e: unknown) => e);
    expect(describeError(err)).toContain('Cannot reach the server');
    expect(describeError(new Error('boom <script>'))).toBe('Unexpected error');
  });

  it('invokes the unauthorized handler on 401 (except for the login call)', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetch(() => ({ status: 401, body: { status: 401 } }));
    await api.getApi(1).catch(() => undefined);
    expect(handler).toHaveBeenCalledTimes(1);
    await api.login('u', 'bad').catch(() => undefined);
    expect(handler).toHaveBeenCalledTimes(1);
  });
});

describe('token decoding (display only)', () => {
  it('decodes claims and rejects malformed tokens without throwing', () => {
    const u = decodeToken(fakeToken({ sub: 'alice', roles: ['ADMIN'], team: 'orders' }));
    expect(u).toMatchObject({ username: 'alice', roles: ['ADMIN'], team: 'orders' });
    expect(decodeToken('not-a-jwt')).toBeNull();
    expect(decodeToken('a.b.c')).toBeNull();
  });
});
