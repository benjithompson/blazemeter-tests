import { defineConfig, devices } from '@playwright/test';

// On BlazeMeter, Taurus overrides --workers, --repeat-each, --project and
// --reporter. A `chromium` project must exist, because Taurus passes
// --project=chromium.
export default defineConfig({
  testDir: '.',
  // bzm-vitals-dashboard caches run artifacts (with spec copies) in this folder.
  testIgnore: ['**/.artifact-cache/**'],
  fullyParallel: true,
  // Always 0. Retries add extra vitals records and change the sample counts.
  retries: 0,
  timeout: 60_000,
  use: {
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
