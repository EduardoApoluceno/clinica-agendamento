/**
 * Cliente HTTP do BFF: usa apenas o cookie de sessão (HttpOnly) e envia o token CSRF
 * lido do cookie XSRF-TOKEN. Nenhum token OAuth passa pelo navegador.
 */
export const URL_LOGIN = '/oauth2/authorization/keycloak'

const METODOS_SEGUROS = new Set(['GET', 'HEAD', 'OPTIONS'])

export class ErroApi extends Error {
  readonly status: number
  readonly problema: ProblemDetails | undefined

  constructor(status: number, problema?: ProblemDetails) {
    super(problema?.detail ?? `Erro ${status}`)
    this.status = status
    this.problema = problema
  }
}

export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  correlationId?: string
}

export function lerCookie(nome: string, cookies: string = document.cookie): string | undefined {
  const par = cookies.split('; ').find((c) => c.startsWith(`${nome}=`))
  return par ? decodeURIComponent(par.slice(nome.length + 1)) : undefined
}

export async function api<T>(caminho: string, init: RequestInit = {}): Promise<T> {
  const metodo = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (!METODOS_SEGUROS.has(metodo)) {
    const csrf = lerCookie('XSRF-TOKEN')
    if (csrf) headers.set('X-XSRF-TOKEN', csrf)
  }
  const resposta = await fetch(caminho, { ...init, headers, credentials: 'same-origin' })
  if (!resposta.ok) {
    const problema = resposta.headers.get('content-type')?.includes('json')
      ? ((await resposta.json()) as ProblemDetails)
      : undefined
    throw new ErroApi(resposta.status, problema)
  }
  return resposta.status === 204 ? (undefined as T) : ((await resposta.json()) as T)
}

export function irParaLogin() {
  window.location.assign(URL_LOGIN)
}

/**
 * Token CSRF no formato que o Spring Security espera em campo de formulário (`csrf.spa()` aceita o valor cru
 * só no cabeçalho X-XSRF-TOKEN): base64url(aleatório ‖ token XOR aleatório), como o XorCsrfTokenRequestAttributeHandler.
 */
export function mascararCsrf(token: string, aleatorio?: Uint8Array): string {
  const bytes = new TextEncoder().encode(token)
  const sal = aleatorio ?? crypto.getRandomValues(new Uint8Array(bytes.length))
  const combinado = new Uint8Array(bytes.length * 2)
  combinado.set(sal)
  bytes.forEach((b, i) => { combinado[bytes.length + i] = b ^ sal[i]! })
  return btoa(String.fromCharCode(...combinado)).replaceAll('+', '-').replaceAll('/', '_')
}

/** Logout global: POST com CSRF; o backend encerra a sessão local e no Keycloak. */
export function sair() {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = '/logout'
  const csrf = lerCookie('XSRF-TOKEN')
  if (csrf) {
    const campo = document.createElement('input')
    campo.type = 'hidden'
    campo.name = '_csrf'
    campo.value = mascararCsrf(csrf)
    form.appendChild(campo)
  }
  document.body.appendChild(form)
  form.submit()
}
