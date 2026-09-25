import { defineConfig, devices } from "@playwright/test";
import { FRONTEND_URL } from "./scripts/servers";

/**
 * E2E journeys against the real stack (spec/test-strategy.md §13). One worker: some journeys restart the backend.
 * The browser only talks to the Next.js origin; API calls made by tests go through the same /api proxy.
 */
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 7_000 },
  reporter: [["list"], ["json", { outputFile: `.state/results-${process.env.E2E_BACKEND ?? "real"}.json` }]],
  globalSetup: "./scripts/global-setup.ts",
  globalTeardown: "./scripts/global-teardown.ts",
  use: {
    baseURL: FRONTEND_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  // D-4 supported browsers: Chrome/Edge (Chromium), Firefox, Safari (WebKit). Chromium runs by default;
  // E2E_BROWSERS=all (npm run e2e:all-browsers) adds the other two engines.
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    ...(process.env.E2E_BROWSERS === "all"
      ? [
          { name: "firefox", use: { ...devices["Desktop Firefox"] } },
          { name: "webkit", use: { ...devices["Desktop Safari"] } },
        ]
      : []),
  ],
});
