import { defineConfig, devices } from "@playwright/test";

/**
 * E2E tests run against the running dev stack:
 * - Frontend: http://localhost:5173 (Vite dev server)
 * - Backend:  http://localhost:8080 (Spring Boot)
 *
 * Start the full stack with `npm run dev` from the monorepo root before running
 * these tests, or use the webServer config below to start them automatically.
 */
export default defineConfig({
  testDir: ".",
  testMatch: "**/*.spec.ts",
  fullyParallel: false, // Avoid DB race conditions
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: process.env.CI ? "html" : "list",

  timeout: process.env.CI ? 60_000 : 30_000,
  expect: {
    timeout: process.env.CI ? 15_000 : 5_000,
  },
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    actionTimeout: process.env.CI ? 15_000 : 10_000,
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  // Optionally start the dev servers automatically:
  // webServer: [
  //   {
  //     command: "cd ../backend && ./gradlew bootRun",
  //     url: "http://localhost:8080/health",
  //     reuseExistingServer: true,
  //     timeout: 30_000,
  //   },
  //   {
  //     command: "cd ../frontend && npm run dev",
  //     url: "http://localhost:5173",
  //     reuseExistingServer: true,
  //     timeout: 10_000,
  //   },
  // ],
});
