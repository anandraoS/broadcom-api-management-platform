/// <reference types="vitest" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Same-origin API in development: /api/ and /graphql are proxied to the control plane (note the trailing slash on /api/
// so the SPA route /apis is NOT captured - the same bug the Angular app hit).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/': { target: 'http://localhost:8080', changeOrigin: false },
      '/graphql': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: { provider: 'v8', reporter: ['text-summary', 'lcov'] },
  },
});
