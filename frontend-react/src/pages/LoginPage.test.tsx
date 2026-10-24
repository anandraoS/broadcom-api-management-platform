import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { LoginPage } from './LoginPage';
import { fakeToken, mockFetch, renderApp } from '../test/helpers';

describe('LoginPage', () => {
  it('does not call the server when the form is invalid and shows field errors', async () => {
    const f = mockFetch(() => ({ body: {} }));
    renderApp(<LoginPage />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(f).not.toHaveBeenCalled();
    expect(screen.getByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('shows one generic message on failure (no user enumeration)', async () => {
    mockFetch(() => ({ status: 401, body: { code: 'invalid_credentials', detail: 'Invalid username or password' } }));
    renderApp(<LoginPage />);
    await userEvent.type(screen.getByLabelText('Username'), 'ghost');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Sign-in failed');
    expect(alert).not.toHaveTextContent('ghost');
  });

  it('stores the session for the tab only after a successful login', async () => {
    const token = fakeToken({ sub: 'admin', roles: ['ADMIN'] });
    mockFetch(() => ({ body: { accessToken: token, tokenType: 'Bearer', expiresIn: 3600, roles: ['ADMIN'], team: 'platform' } }));
    renderApp(<LoginPage />);
    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'pw');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(sessionStorage.getItem('apiplatform.session')).toBe(token));
    expect(localStorage.getItem('apiplatform.session')).toBeNull();
  });
});
