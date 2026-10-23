import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '@/components/ui/toast'
import { MeuCadastroPage } from './MeuCadastroPage'

const paciente = (verificadoEm?: string) => ({
  id: 'p1',
  nome: 'Carla Mendes',
  dataNascimento: '1990-05-10',
  cpf: '529.982.247-25',
  canalPreferido: 'WHATSAPP',
  origem: 'PORTAL',
  possuiConta: true,
  contatos: [{ tipo: 'CELULAR', valor: '+5569999991234', principal: true, ...(verificadoEm ? { verificadoEm } : {}) }],
  versao: 0,
})

const json = (status: number, corpo?: unknown) =>
  new Response(corpo === undefined ? null : JSON.stringify(corpo), {
    status,
    headers: { 'content-type': 'application/json' },
  })

/** Backend simulado: responde por método + caminho e guarda o que foi enviado. */
function simularBackend(rotas: Record<string, (corpo: unknown) => Response>) {
  const enviados: Record<string, unknown> = {}
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (entrada, init) => {
    const chave = `${init?.method ?? 'GET'} ${String(entrada).split('?')[0]}`
    const corpo = init?.body ? JSON.parse(String(init.body)) : undefined
    enviados[chave] = corpo
    const rota = rotas[chave]
    if (!rota) throw new Error(`Rota não simulada: ${chave}`)
    return rota(corpo)
  })
  return enviados
}

function renderizar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MeuCadastroPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('MeuCadastroPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-teste'
  })
  afterEach(() => vi.restoreAllMocks())

  it('completa o cadastro e confirma o celular com o código', async () => {
    const usuario = userEvent.setup()
    const enviados = simularBackend({
      'GET /api/v1/me/paciente': () => json(404, { detail: 'paciente não encontrado' }),
      'POST /api/v1/me/paciente': () => json(201, paciente()),
      'GET /api/v1/me/consentimentos': () => json(200, []),
      'POST /api/v1/me/telefone/codigo': () =>
        json(200, { destinoMascarado: '(69) *****-1234', expiraEm: '2026-10-23T12:10:00Z', codigoDemo: '123456' }),
      'POST /api/v1/me/telefone/verificacao': () => json(200, paciente('2026-10-23T12:01:00Z')),
    })
    renderizar()

    await usuario.type(await screen.findByLabelText(/Nome completo/), 'Carla Mendes')
    await usuario.type(screen.getByLabelText(/^CPF/), '52998224725')
    await usuario.type(screen.getByLabelText(/Data de nascimento/), '1990-05-10')
    await usuario.type(screen.getByLabelText(/^Celular/), '69999991234')
    await usuario.click(screen.getByRole('button', { name: 'Concluir cadastro' }))

    expect(await screen.findByRole('heading', { name: 'Confirme seu celular' })).toBeInTheDocument()
    expect(enviados['POST /api/v1/me/paciente']).toEqual({
      dados: { nome: 'Carla Mendes', dataNascimento: '1990-05-10', cpf: '529.982.247-25', canalPreferido: 'WHATSAPP' },
      celular: '(69) 99999-1234',
    })

    await usuario.click(screen.getByRole('button', { name: 'Enviar código' }))
    expect(await screen.findByText('123456')).toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Código'), '123456')
    await usuario.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByText(/confirmado em/)).toBeInTheDocument()
    expect(enviados['POST /api/v1/me/telefone/verificacao']).toEqual({ codigo: '123456' })
  })

  it('recusa CPF inválido sem chamar a API', async () => {
    const usuario = userEvent.setup()
    const enviados = simularBackend({
      'GET /api/v1/me/paciente': () => json(404, {}),
    })
    renderizar()

    await usuario.type(await screen.findByLabelText(/Nome completo/), 'Carla Mendes')
    await usuario.type(screen.getByLabelText(/^CPF/), '11111111111')
    await usuario.click(screen.getByRole('button', { name: 'Concluir cadastro' }))

    expect(await screen.findByText('CPF inválido.')).toBeInTheDocument()
    expect(enviados['POST /api/v1/me/paciente']).toBeUndefined()
  })
})
