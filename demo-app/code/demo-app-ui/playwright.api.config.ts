import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/integration',
  // Keep API tests outside Vitest's *.test.* / *.spec.* component discovery.
  testMatch: 'claim-submission-status.api.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  use: {
    baseURL: process.env.QA_BFF_URL ?? 'http://localhost:8090',
    trace: 'off',
  },
  outputDir: 'test-results/claim-submission-status',
  reporter: [
    ['list'],
    ['json', { outputFile: 'test-results/claim-submission-status.report.json' }],
  ],
});
