import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import ApiDetailPage, { buildConfig, formErrors } from './ApiDetailPage';
import type { Api } from '../core/models';
import { mockFetch, renderApp, signIn } from '../test/helpers';

const API: Api = {
  id: 5, slug: 'orders', name: 'Orders', ownerTeam: 'orders', lifecycle: 'ACTIVE', description: 'd', version: 3,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
  versions: [{ id: 9, label: 'v1', basePath: '/orders/v1', status: 'ACTIVE', hasSpec: false, targets: [] }],
};

function page() {
  return renderApp(<Routes><Route path="/apis/:id" element={<ApiDetailPage />} /></Routes>, '/apis/5');
}

describe('ApiDetailPage', () => {
  it('renders read-only for an owner of another team (server still enforces)', async () => {
    signIn({ roles: ['API_OWNER'], team: 'payments' });
    mockFetch(() => ({ body: API }));
    page();
    expect(await screen.findByText(/Read-only/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
  });

  it('offers edit controls to the owning team and only valid lifecycle transitions', async () => {
    signIn({ roles: ['API_OWNER'], team: 'orders' });
    mockFetch(() => ({ body: API }));
    page();
    expect(await screen.findByRole('button', { name: 'Save' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Move to INACTIVE' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Move to DEPRECATED' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Move to ACTIVE' })).not.toBeInTheDocument();
  });

  it('shows the destination-policy errors when the server rejects a backend target (SSRF)', async () => {
    signIn({ roles: ['ADMIN'] });
    mockFetch((url, init) => (init.method === 'POST' && url.includes('/targets')
      ? { status: 422, body: { status: 422, code: 'unsafe_destination', detail: 'Backend destination rejected', errors: ['target host is not in the destination allowlist: 169.254.169.254'] } }
      : { body: API }));
    page();
    await userEvent.click(await screen.findByRole('tab', { name: 'Versions & targets' }));
    await userEvent.type(screen.getByLabelText('Backend URL'), 'http://169.254.169.254');
    await userEvent.click(screen.getByRole('button', { name: 'Add target' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('destination allowlist');
  });

  it('blocks submitting an invalid policy form without calling the server', async () => {
    signIn({ roles: ['ADMIN'] });
    const f = mockFetch((url) => ({ body: url.endsWith('/policies') ? [] : API }));
    page();
    await userEvent.click(await screen.findByRole('tab', { name: 'Policies' }));
    const rate = await screen.findByLabelText('Rate limit per minute');
    await userEvent.clear(rate);
    await userEvent.type(rate, '0');
    const before = f.mock.calls.length;
    await userEvent.click(screen.getByRole('button', { name: 'Submit for approval' }));
    expect(screen.getByText('1 - 6,000,000')).toBeInTheDocument();
    expect(f.mock.calls.slice(before).some(([, init]) => (init as RequestInit)?.method === 'POST')).toBe(false);
  });
});

describe('policy form helpers', () => {
  const base = { auth: 'API_KEY' as const, requiredRole: '', allowedMethods: ['GET'], rateLimitPerMinute: 100, burst: 20, rateAlgorithm: 'TOKEN_BUCKET' as const,
    timeoutMs: 3000, maxRequestBytes: 65536, ipAllowCidrs: ' 10.0.0.0/8 , ,192.168.1.0/24', retryEnabled: true, comment: '' };

  it('builds the wire config: trims CIDRs, nulls empty fields', () => {
    const c = buildConfig(base);
    expect(c.ipAllowCidrs).toEqual(['10.0.0.0/8', '192.168.1.0/24']);
    expect(c.requiredRole).toBeNull();
    expect(buildConfig({ ...base, ipAllowCidrs: '' }).ipAllowCidrs).toBeNull();
  });

  it('mirrors the server validation bounds (server stays authoritative)', () => {
    expect(formErrors(base)).toEqual({});
    expect(Object.keys(formErrors({ ...base, rateLimitPerMinute: 0, timeoutMs: 5, allowedMethods: [] })).sort()).toEqual(['allowedMethods', 'rateLimitPerMinute', 'timeoutMs']);
  });
});
