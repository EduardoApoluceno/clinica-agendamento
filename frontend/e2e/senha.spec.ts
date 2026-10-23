import { expect, test } from '@playwright/test'
import { SENHA_PACIENTE, criarConta, definirSenha, entrarNoKeycloak, linkDeVerificacao } from './apoio'

const SENHA_NOVA = 'Redefinida#2026kW'

test('"Esqueceu sua senha?" troca a senha pelo link do e-mail', async ({ page }) => {
  const email = await criarConta(page, 'Sara', 'Teste')
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page.locator('#username')).toBeVisible()

  const pedidoEm = new Date()
  await page.getByRole('link', { name: 'Esqueceu sua senha?' }).click()
  await page.locator('#username').fill(email)
  await page.locator('#kc-reset-password-form [type=submit]').click()

  await page.goto(await linkDeVerificacao(email, pedidoEm))
  await definirSenha(page, SENHA_NOVA)
  if (page.url().includes('/realms/')) await page.goto('/')
  await expect(page.getByRole('heading', { name: /^Olá,/ })).toBeVisible()

  // A senha antiga deixa de valer; a nova entra.
  await page.getByRole('button', { name: 'Sair' }).click()
  await entrarNoKeycloak(page, email, SENHA_PACIENTE)
  await expect(page.getByText('Nome de usuário ou senha inválida')).toBeVisible()
  await entrarNoKeycloak(page, email, SENHA_NOVA)
  await expect(page.getByRole('heading', { name: /^Olá,/ })).toBeVisible()
})
