import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests of the whole app: the built React app served by the real Spring Boot server, sign-in
 * included. Run with `npm run e2e` (which builds the frontend first) or `make e2e`.
 */
const PORT = 5077;

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    timezoneId: 'America/New_York',
    locale: 'en-US',
    // Locally, drive the Chrome that's already installed; CI installs Playwright's Chromium.
    channel: process.env.CI ? undefined : 'chrome',
    trace: 'retain-on-failure',
  },
  webServer: {
    // Packages the jar (quick when nothing changed), then runs it exactly as production would.
    command: './mvnw -q -B -DskipTests package && java -jar target/brew-focus.jar',
    cwd: '../backend',
    url: `http://127.0.0.1:${PORT}/api/health`,
    timeout: 240_000,
    reuseExistingServer: false,
    env: {
      PORT: String(PORT),
      BIND_ADDRESS: '127.0.0.1',
      DB_PATH: ':memory:',
      STATIC_DIR: '../frontend/dist',
      SPRING_PROFILES_ACTIVE: 'prod',
      JWT_SECRET: 'e2e-secret-e2e-secret-e2e-secret-0123',
      SECURE_COOKIES: 'false',
    },
  },
});
