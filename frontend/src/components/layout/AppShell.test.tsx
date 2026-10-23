import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioAtual } from '@/features/sessao/useUsuarioAtual'
import { AppShell, itensVisiveis } from './AppShell'

const recepcao: UsuarioAtual = { id: '1', nome: 'Renata Recepção', papeis: ['RECEPCAO'], ambienteDemo: true }
const paciente: UsuarioAtual = { id: '2', nome: 'Carla Mendes', papeis: ['PACIENTE'], ambienteDemo: false }

describe('itensVisiveis', () => {
  it('mostra à recepção agenda, agendamento e caixa de saída (demo)', () => {
    expect(itensVisiveis(recepcao).map((i) => i.rota)).toEqual(['/agenda', '/agendar', '/caixa-saida'])
  })

  it('mostra ao paciente apenas o agendamento', () => {
    expect(itensVisiveis(paciente).map((i) => i.rota)).toEqual(['/agendar'])
  })
})

describe('AppShell', () => {
  it('abre o menu no celular pelo botão acessível', async () => {
    render(
      <MemoryRouter>
        <AppShell usuario={recepcao}>conteúdo</AppShell>
      </MemoryRouter>,
    )
    const botao = screen.getByRole('button', { name: 'Abrir menu' })
    expect(botao).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(botao)

    expect(screen.getByRole('button', { name: 'Fechar menu' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('link', { name: /Agenda do dia/ })).toBeInTheDocument()
  })
})
