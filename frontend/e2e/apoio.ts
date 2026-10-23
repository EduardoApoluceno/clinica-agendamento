import { createHmac, randomInt } from 'node:crypto'
import { expect, type Page } from '@playwright/test'

export const SENHA_DEMO = process.env.KC_DEMO_SENHA ?? 'Demo@2026!'
export const SENHA_PACIENTE = 'Paciente#2026xY'
const MAILPIT = process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025'
const PASSO_TOTP_S = 30

/** TOTP (RFC 6238, HMAC-SHA1, 6 dígitos, 30 s): a mesma política do realm. */
export function codigoTotp(segredoBase32: string, passo = Math.floor(Date.now() / 1000 / PASSO_TOTP_S)): string {
  const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  const bits = [...segredoBase32.replace(/[\s=]/g, '').toUpperCase()]
    .map((c) => alfabeto.indexOf(c).toString(2).padStart(5, '0'))
    .join('')
  const chave = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)))
  const contador = Buffer.alloc(8)
  contador.writeBigUInt64BE(BigInt(passo))
  const hmac = createHmac('sha1', chave).update(contador).digest()
  const deslocamento = hmac[hmac.length - 1]! & 0x0f
  return String((hmac.readUInt32BE(deslocamento) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

export function passoTotpAtual() {
  return Math.floor(Date.now() / 1000 / PASSO_TOTP_S)
}

/** O Keycloak não aceita o mesmo código duas vezes: espera a próxima janela de 30 s. */
export async function esperarProximoPasso(page: Page, passoUsado: number) {
  while (passoTotpAtual() <= passoUsado) await page.waitForTimeout(1000)
}

/** CPF aleatório com dígitos verificadores válidos (cada execução usa um paciente novo). */
export function cpfAleatorio(): string {
  const base = Array.from({ length: 9 }, () => randomInt(10))
  const digito = (numeros: number[]) =>
    ((numeros.reduce((soma, n, i) => soma + n * (numeros.length + 1 - i), 0) * 10) % 11) % 10
  const d1 = digito(base)
  const d2 = digito([...base, d1])
  return [...base, d1, d2].join('')
}

export function celularAleatorio(): string {
  return `699${String(randomInt(10_000_000, 99_999_999))}`
}

/** Preenche usuário e senha na tela de login do Keycloak (tema keycloak.v2). */
export async function entrarNoKeycloak(page: Page, usuario: string, senha = SENHA_DEMO) {
  await page.locator('#username').fill(usuario)
  await page.locator('#password').fill(senha)
  await page.locator('#kc-login').click()
}

/**
 * Link de ação (verificação de e-mail, redefinição de senha) do e-mail mais recente enviado ao destinatário
 * (Mailpit do perfil demo). Com `depoisDe`, ignora os e-mails anteriores, para não pegar um link antigo.
 */
export async function linkDeVerificacao(destinatario: string, depoisDe = new Date(0)): Promise<string> {
  let link: string | undefined
  await expect
    .poll(
      async () => {
        const busca = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${destinatario}"`)}`)
        const { messages = [] } = (await busca.json()) as { messages?: { ID: string; Created: string }[] }
        const recente = messages.find((m) => new Date(m.Created) >= depoisDe)
        if (!recente) return undefined
        const mensagem = (await (await fetch(`${MAILPIT}/api/v1/message/${recente.ID}`)).json()) as { HTML: string }
        link = /href="([^"]*action-token[^"]*)"/.exec(mensagem.HTML)?.[1]?.replaceAll('&amp;', '&')
        return link
      },
      { message: `e-mail de verificação para ${destinatario}`, timeout: 30_000 },
    )
    .toBeTruthy()
  return link!
}

/** Define a senha na tela do Keycloak que vem depois de um link de verificação ou de redefinição. */
export async function definirSenha(page: Page, senha: string) {
  await page.locator('#password-new').fill(senha)
  await page.locator('#password-confirm').fill(senha)
  await page.locator('#kc-passwd-update-form #kc-submit').click()
}

/**
 * Autocadastro no Keycloak até entrar no app; devolve o e-mail da conta nova. Com verificação de e-mail ligada,
 * o Keycloak 26 não pede a senha no formulário: ela é definida logo depois de abrir o link do e-mail
 * (só quem prova o e-mail escolhe a senha).
 */
export async function criarConta(page: Page, nome: string, sobrenome: string): Promise<string> {
  const email = `e2e-${nome.toLowerCase()}-${Date.now()}@exemplo.com`
  await page.goto('/')
  await page.getByRole('link', { name: 'Cadastre-se' }).click()
  await page.locator('#email').fill(email)
  await page.locator('#firstName').fill(nome)
  await page.locator('#lastName').fill(sobrenome)
  await page.locator('#kc-register-form [type=submit]').click()

  await page.goto(await linkDeVerificacao(email))
  await definirSenha(page, SENHA_PACIENTE)
  // Na mesma sessão o Keycloak volta direto ao app; se parar numa página informativa, o SSO resolve.
  if (page.url().includes('/realms/')) await page.goto('/')
  await expect(page.getByRole('heading', { name: /^Olá,/ })).toBeVisible()
  return email
}
