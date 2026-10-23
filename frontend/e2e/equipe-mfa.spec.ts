import { expect, test } from '@playwright/test'
import { codigoTotp, entrarNoKeycloak, esperarProximoPasso, passoTotpAtual } from './apoio'

// Usuário da equipe sem TOTP no realm de demonstração: o 1º login força o cadastro do 2º fator.
const USUARIO = process.env.E2E_USUARIO_EQUIPE ?? 'dr.bruno@clinica.local'

// O 2º teste depende do TOTP cadastrado no 1º.
test.describe.configure({ mode: 'serial' })

test('equipe cadastra o TOTP no 1º login e o usa no login seguinte', async ({ page }) => {
  await page.goto('/')
  await entrarNoKeycloak(page, USUARIO)

  // Cadastro obrigatório do autenticador (ação CONFIGURE_TOTP).
  await page.locator('#mode-manual').click()
  const segredo = (await page.locator('#kc-totp-secret-key').innerText()).trim()
  const passoCadastro = passoTotpAtual()
  await page.locator('#totp').fill(codigoTotp(segredo, passoCadastro))
  await page.locator('#userLabel').fill('Playwright')
  await page.locator('#saveTOTPBtn').click()

  await expect(page.getByRole('heading', { name: /^Olá,/ })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Menu principal' })).toBeVisible()

  // Logout global: a sessão no Keycloak também acaba, então o próximo acesso pede senha e código.
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page.locator('#username')).toBeVisible()
  await entrarNoKeycloak(page, USUARIO)

  await expect(page.locator('#otp')).toBeVisible()
  await esperarProximoPasso(page, passoCadastro)
  await page.locator('#otp').fill(codigoTotp(segredo))
  await page.locator('#kc-login').click()

  await expect(page.getByRole('heading', { name: /^Olá,/ })).toBeVisible()
})

test('código TOTP errado não entra', async ({ page }) => {
  await page.goto('/')
  await entrarNoKeycloak(page, USUARIO)
  await expect(page.locator('#otp')).toBeVisible()
  await page.locator('#otp').fill('000000')
  await page.locator('#kc-login').click()

  await expect(page.locator('#otp')).toBeVisible()
  await expect(page.getByRole('heading', { name: /^Olá,/ })).toHaveCount(0)
})
