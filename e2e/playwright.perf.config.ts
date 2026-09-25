import { defineConfig } from "@playwright/test";

/**
 * D-4 performance check (npm run perf): 100 000 tickets on a throwaway PostgreSQL, API latency measured against the
 * real backend. Separate from the E2E suite because seeding and measuring take minutes and need a quiet machine.
 */
export default defineConfig({
  testDir: "./perf",
  workers: 1,
  retries: 0,
  timeout: 15 * 60_000,
  reporter: [["list"]],
});
