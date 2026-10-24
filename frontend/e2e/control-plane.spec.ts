import { Page, expect, test } from '@playwright/test';

// Dummy local-only credentials of the dev identity provider (control-plane "local" profile / .env.example).
const PASSWORD = process.env['DEV_AUTH_PASSWORD'] ?? 'local-demo-password';

async function login(page: Page, user: string, password = PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username').fill(user);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
}

test('rejects a wrong password with a generic message', async ({ page }) => {
  await login(page, 'admin', 'definitely-wrong');
  await expect(page.getByRole('alert')).toContainText('Sign-in failed');
  await expect(page).toHaveURL(/\/login$/);
});

test('unauthenticated users are redirected to the login page', async ({ page }) => {
  await page.goto('/apis');
  await expect(page).toHaveURL(/\/login$/);
});

test('admin can sign in, browse the registry and open an API with its policy history', async ({ page }) => {
  await login(page, 'admin');
  await expect(page.getByTestId('whoami')).toContainText('admin');
  await page.getByRole('link', { name: 'API registry' }).click();
  await expect(page.getByRole('table', { name: 'APIs' })).toContainText('Orders API');
  await page.getByRole('link', { name: 'Orders API' }).click();
  await expect(page.getByRole('heading', { name: /Orders API/ })).toBeVisible();
  await page.getByRole('tab', { name: 'Policies' }).click();
  await expect(page.getByRole('table', { name: 'Policy history' })).toContainText('ACTIVE');
});

test('registry filter narrows the list and shows an empty state when nothing matches', async ({ page }) => {
  await login(page, 'admin');
  await page.getByRole('link', { name: 'API registry' }).click();
  await page.getByLabel('Search name or slug').fill('zzz-no-such-api');
  await expect(page.getByText('No APIs match these filters.')).toBeVisible();
});

test('server rejects an SSRF backend target and the UI explains why', async ({ page }) => {
  await login(page, 'admin');
  await page.getByRole('link', { name: 'API registry' }).click();
  await page.getByRole('link', { name: 'Orders API' }).click();
  await page.getByRole('tab', { name: 'Versions & targets' }).click();
  await page.getByLabel('Backend URL').first().fill('http://169.254.169.254');
  await page.getByRole('button', { name: 'Add target' }).first().click();
  await expect(page.getByRole('alert')).toContainText('destination');
});

test('viewer does not see approvals or audit in the navigation and is redirected from them', async ({ page }) => {
  await login(page, 'viewer');
  await expect(page.getByRole('link', { name: 'API registry' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Policy approvals' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Audit history' })).toHaveCount(0);
  await page.goto('/approvals'); // UX guard only; the API would answer 403 anyway
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
});

test('operator can open the incident investigation with evidence and suggested steps', async ({ page }) => {
  await login(page, 'operator');
  await page.getByRole('link', { name: 'Incident investigation' }).click();
  await expect(page.getByText('Findings (')).toBeVisible();
  await expect(page.getByText('deterministic threshold rules')).toBeVisible();
});

test('approver sees the audit history', async ({ page }) => {
  await login(page, 'approver');
  await page.getByRole('link', { name: 'Audit history' }).click();
  await expect(page.getByRole('table', { name: 'Audit events' })).toBeVisible();
});
