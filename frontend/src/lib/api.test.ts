import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ErroApi, lerCookie, mascararCsrf } from './api'

describe('lerCookie', () => {
  it('lê o valor decodificado do cookie pedido', () => {
    expect(lerCookie('XSRF-TOKEN', 'a=1; XSRF-TOKEN=abc%3D%3D; b=2')).toBe('abc==')
  })

  it('retorna undefined quando o cookie não existe', () => {
    expect(lerCookie('XSRF-TOKEN', 'a=1')).toBeUndefined()
  })
})

describe('api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('envia o token CSRF em requisições que alteram estado', async () => {
    document.cookie = 'XSRF-TOKEN=token-123'
    const fetchFalso = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchFalso)

    await api('/api/v1/algo', { method: 'POST' })

    const [, init] = fetchFalso.mock.calls[0] as [string, RequestInit]
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('token-123')
    expect(init.credentials).toBe('same-origin')
  })

  it('não envia CSRF em GET', async () => {
    document.cookie = 'XSRF-TOKEN=token-123'
    const fetchFalso = vi.fn().mockResolvedValue(Response.json({ ok: true }))
    vi.stubGlobal('fetch', fetchFalso)

    await api('/api/v1/algo')

    const [, init] = fetchFalso.mock.calls[0] as [string, RequestInit]
    expect(new Headers(init.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('converte Problem Details em ErroApi', async () => {
    const problema = { status: 409, detail: 'O horário escolhido não está mais disponível.' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(problema, { status: 409 })))

    const erro = await api('/api/v1/agendamentos', { method: 'POST' }).catch((e: unknown) => e)

    expect(erro).toBeInstanceOf(ErroApi)
    expect((erro as ErroApi).status).toBe(409)
    expect((erro as ErroApi).message).toBe(problema.detail)
  })
})

describe('mascararCsrf', () => {
  it('gera base64url(aleatório ‖ token XOR aleatório), como o Spring Security espera no formulário', () => {
    const sal = new Uint8Array([1, 2, 3])
    // 'abc' = 0x61 0x62 0x63 → XOR 01 02 03 = 0x60 0x60 0x60
    expect(mascararCsrf('abc', sal)).toBe(btoa(String.fromCharCode(1, 2, 3, 0x60, 0x60, 0x60)))
  })

  it('muda a cada chamada e recupera o token original', () => {
    const token = '4b2c9e1f-7d3a-4e8b-9c0d-a1b2c3d4e5f6'
    const a = mascararCsrf(token)
    expect(a).not.toBe(mascararCsrf(token))
    const bytes = Uint8Array.from(atob(a.replaceAll('-', '+').replaceAll('_', '/')), (c) => c.charCodeAt(0))
    const metade = bytes.length / 2
    const original = bytes.slice(metade).map((b, i) => b ^ bytes[i]!)
    expect(new TextDecoder().decode(original)).toBe(token)
  })
})
