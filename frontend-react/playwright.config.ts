import { defineConfig } from '@playwright/test';

/**
 * Same end-to-end suite as the Angular console (identical accessible names/roles), pointed at the React dev server on :4300.
 * Needs the local stack: scripts/run-local.sh start. E2E_CHANNEL=msedge uses the installed Edge.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  reporter: [['list']],
  use: { baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4300', channel: process.env['E2E_CHANNEL'] || undefined, headless: true, trace: 'retain-on-failure' },
  webServer: process.env['E2E_BASE_URL']
    ? undefined
    : { command: 'npm run start', url: 'http://localhost:4300', reuseExistingServer: true, timeout: 120_000 },
});
