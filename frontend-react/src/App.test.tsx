import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { renderApp, signIn } from './test/helpers';

describe('route guards (UX hints, not security)', () => {
  it('redirects anonymous users to the login page', async () => {
    renderApp(<App />, '/apis');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('redirects an expired session to the login page', async () => {
    signIn({ expInSeconds: -10 });
    renderApp(<App />, '/apis');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('sends a viewer away from the approvals page', async () => {
    signIn({ roles: ['VIEWER'] });
    renderApp(<App />, '/approvals');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });
});
