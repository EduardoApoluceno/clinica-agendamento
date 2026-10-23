import { defineConfig, devices } from '@playwright/test'

// Testes de ponta a ponta contra a stack `docker compose --profile demo` (Keycloak real com MFA, Mailpit).
// Rodar: com a stack de pé, `npm run e2e`. Os testes criam dados e cadastram TOTP: use um banco descartável.
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_URL ?? 'http://localhost:8080',
    locale: 'pt-BR',
    timezoneId: 'America/Porto_Velho',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
