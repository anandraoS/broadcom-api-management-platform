import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests run against the REAL stack: ng dev server (proxying /api to the control plane on :8080) and the
 * control plane started with the "local" profile (scripts/run-local.sh start). They are skipped by CI unless the stack
 * is available. E2E_CHANNEL=msedge uses the installed Edge instead of downloading a Playwright browser.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    channel: process.env['E2E_CHANNEL'] || undefined,
    headless: true,
    trace: 'retain-on-failure',
  },
  webServer: process.env['E2E_BASE_URL']
    ? undefined
    : { command: 'npm run start -- --port 4200', url: 'http://localhost:4200', reuseExistingServer: true, timeout: 120_000 },
});
