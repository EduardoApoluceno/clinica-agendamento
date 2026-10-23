import { expect, test, type Page } from '@playwright/test'
import { celularAleatorio, cpfAleatorio, criarConta } from './apoio'

async function completarCadastro(page: Page, nomeCompleto: string) {
  await page.getByLabel(/Nome completo/).fill(nomeCompleto)
  await page.getByLabel(/^CPF/).fill(cpfAleatorio())
  await page.getByLabel(/Data de nascimento/).fill('1990-05-10')
  await page.getByLabel(/^Celular/).fill(celularAleatorio())
  await page.getByRole('button', { name: 'Concluir cadastro' }).click()
}

/** No demo o código também aparece na tela (além da caixa de saída). */
async function pedirCodigo(page: Page) {
  await page.getByRole('button', { name: 'Enviar código' }).click()
  return (await page.getByText(/^\d{6}$/).innerText()).trim()
}

test('paciente cria a conta, completa o cadastro e verifica o celular', async ({ page }) => {
  await criarConta(page, 'Paula', 'Teste')

  // Paciente não vê telas da equipe.
  const menu = page.getByRole('navigation', { name: 'Menu principal' })
  await expect(menu.getByRole('link', { name: 'Pacientes' })).toHaveCount(0)
  await menu.getByRole('link', { name: 'Meu cadastro' }).click()

  await completarCadastro(page, 'Paula Teste')
  const codigo = await pedirCodigo(page)
  await page.getByLabel('Código').fill(codigo)
  await page.getByRole('button', { name: 'Confirmar' }).click()

  await expect(page.getByText(/Celular .* confirmado em/)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Autorizações de uso dos seus dados' })).toBeVisible()
})

test('código de verificação errado é recusado', async ({ page }) => {
  await criarConta(page, 'Rui', 'Teste')
  await page.goto('/meu-cadastro')

  await completarCadastro(page, 'Rui Teste')
  const codigo = await pedirCodigo(page)
  await page.getByLabel('Código').fill(codigo === '000000' ? '111111' : '000000')
  await page.getByRole('button', { name: 'Confirmar' }).click()

  await expect(page.getByRole('alert').filter({ hasText: 'Código incorreto' })).toBeVisible()
  await expect(page.getByText(/confirmado em/)).toHaveCount(0)
})
