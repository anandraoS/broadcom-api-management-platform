import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { Shell } from './Shell';
import { renderApp, signIn } from '../test/helpers';

function shell() {
  return renderApp(<Routes><Route element={<Shell />}><Route path="*" element={<div>page</div>} /></Route></Routes>, '/dashboard');
}

describe('Shell navigation (UX only - the server is authoritative)', () => {
  it('shows approvals and audit only to privileged roles', () => {
    signIn({ roles: ['VIEWER'] });
    shell();
    expect(screen.getByRole('link', { name: 'API registry' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Policy approvals' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Audit history' })).not.toBeInTheDocument();
  });

  it('shows approvals and audit to approvers', () => {
    signIn({ roles: ['POLICY_APPROVER'] });
    shell();
    expect(screen.getByRole('link', { name: 'Policy approvals' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Audit history' })).toBeInTheDocument();
  });

  it('shows the signed-in user and a sign-out control', () => {
    signIn({ sub: 'u', roles: ['OPERATOR'] });
    shell();
    expect(screen.getByTestId('whoami')).toHaveTextContent('u (OPERATOR)');
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
  });
});
