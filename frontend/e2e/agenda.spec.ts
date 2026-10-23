import { expect, test, type Page } from '@playwright/test'
import { codigoTotp, entrarNoKeycloak, passoTotpAtual } from './apoio'

// Contas da semente de demonstração: Carla já tem cadastro com celular verificado (pode agendar sozinha).
const PACIENTE = process.env.E2E_USUARIO_PACIENTE ?? 'carla@exemplo.com'
const RECEPCAO = process.env.E2E_USUARIO_RECEPCAO ?? 'recepcao@clinica.local'

/** @param diasAFrente pula os primeiros dias com vaga (o paciente só cancela com antecedência mínima) */
async function escolherPrimeiroHorarioLivre(page: Page, diasAFrente = 0) {
  if (diasAFrente) {
    await page.getByRole('radiogroup', { name: 'Dia' }).getByRole('radio').and(page.locator(':enabled')).nth(diasAFrente).click()
  }
  const horario = page.getByRole('radiogroup', { name: 'Horário' }).getByRole('radio').and(page.locator(':enabled')).first()
  await horario.click()
  await expect(horario).toHaveAttribute('aria-checked', 'true')
}

test('paciente agenda pelo portal em 4 etapas e cancela em "Meus agendamentos"', async ({ page }) => {
  await page.goto('/')
  await entrarNoKeycloak(page, PACIENTE)
  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Agendar consulta' }).click()

  const progresso = page.getByRole('progressbar', { name: 'Progresso do agendamento' })
  await expect(progresso).toHaveAttribute('aria-valuenow', '1')
  await page.getByRole('radio', { name: /^Consulta/ }).click()
  await page.getByRole('button', { name: 'Continuar' }).click()
  await page.getByRole('radio', { name: /Dra\. Ana Souza/ }).click()
  await page.getByRole('button', { name: 'Continuar' }).click()
  await escolherPrimeiroHorarioLivre(page, 2)
  await page.getByRole('button', { name: 'Continuar' }).click()
  await expect(progresso).toHaveAttribute('aria-valuenow', '4')
  await page.getByRole('button', { name: 'Confirmar agendamento' }).click()

  await expect(page.getByRole('heading', { name: 'Consulta agendada' })).toBeVisible()
  // "sexta-feira, 23 de outubro, às 09:00" → na lista aparece "sexta-feira, 23 de outubro, 09:00–09:30"
  const [dia, horaMarcada] = (await page.getByText(/, às \d{2}:\d{2}$/).innerText()).split(', às ')
  await expect(page.getByRole('link', { name: 'Adicionar ao Google Agenda' })).toHaveAttribute('href', /calendar\.google\.com/)
  await page.getByRole('link', { name: 'Ver meus agendamentos' }).click()

  const proximo = page.getByRole('listitem').filter({ hasText: `${dia}, ${horaMarcada}–` })
  await expect(proximo).toBeVisible()
  await proximo.getByRole('button', { name: 'Cancelar' }).click()
  await proximo.getByRole('button', { name: 'Confirmar cancelamento' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Consulta cancelada' })).toBeVisible()
})

test('recepção vê a grade do dia e agenda um paciente pelo painel', async ({ page }) => {
  await page.goto('/')
  await entrarNoKeycloak(page, RECEPCAO)
  // 1º login da equipe: cadastro obrigatório do TOTP (banco descartável, como no teste de MFA).
  await page.locator('#mode-manual').click()
  const segredo = (await page.locator('#kc-totp-secret-key').innerText()).trim()
  await page.locator('#totp').fill(codigoTotp(segredo, passoTotpAtual()))
  await page.locator('#saveTOTPBtn').click()

  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Agenda do dia' }).click()
  await expect(page.getByRole('list', { name: 'Agenda de Dra. Ana Souza' })).toBeVisible()
  await expect(page.getByRole('list', { name: 'Agenda de Dr. Bruno Lima' })).toBeVisible()
  await expect(page.getByLabel('Resumo do dia')).toContainText('Agendados')

  await page.getByRole('button', { name: 'Novo agendamento' }).click()
  const painel = page.getByRole('region', { name: 'Novo agendamento' })
  await painel.getByRole('searchbox', { name: /Paciente/ }).fill('Helena')
  await painel.getByRole('button', { name: /Helena Prado/ }).click()
  await painel.getByRole('combobox', { name: /Serviço/ }).selectOption({ label: 'Consulta (30 min)' })
  await painel.getByRole('combobox', { name: /Profissional/ }).selectOption({ label: 'Dra. Ana Souza' })
  await escolherPrimeiroHorarioLivre(page)
  await painel.getByRole('button', { name: 'Agendar', exact: true }).click()

  await expect(page.getByRole('status').filter({ hasText: /^Agendado:/ })).toBeVisible()
})
