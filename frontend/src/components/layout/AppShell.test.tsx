import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioAtual } from '@/features/sessao/useUsuarioAtual'
import { AppShell, itensVisiveis } from './AppShell'

const recepcao: UsuarioAtual = {
  id: '1',
  nome: 'Renata Recepção',
  papeis: ['RECEPCAO'],
  permissoes: ['clinica:ler', 'profissionais:ler', 'ausencias:gerir', 'agenda:ler', 'agenda:escrever', 'pacientes:ler',
    'pacientes:escrever', 'caixa-saida:ler'],
  ambienteDemo: true,
}
const paciente: UsuarioAtual = {
  id: '2',
  nome: 'Carla Mendes',
  papeis: ['PACIENTE'],
  permissoes: ['cadastro-proprio:gerir', 'agendamento-proprio:gerir'],
  ambienteDemo: false,
}
const profissional: UsuarioAtual = {
  id: '3',
  nome: 'Dra. Ana Lima',
  papeis: ['PROFISSIONAL'],
  permissoes: ['clinica:ler', 'profissionais:ler', 'agenda-propria:gerir', 'pacientes:ler'],
  ambienteDemo: false,
}

describe('itensVisiveis', () => {
  it('mostra à recepção a agenda, pacientes, equipe e a caixa de saída (demo)', () => {
    expect(itensVisiveis(recepcao).map((i) => i.rota)).toEqual([
      '/agenda', '/pacientes', '/profissionais', '/servicos', '/caixa-saida',
    ])
  })

  it('esconde a caixa de saída fora da demonstração', () => {
    expect(itensVisiveis({ ...recepcao, ambienteDemo: false }).map((i) => i.rota)).not.toContain('/caixa-saida')
  })

  it('mostra ao paciente o agendamento e o próprio cadastro', () => {
    expect(itensVisiveis(paciente).map((i) => i.rota)).toEqual(['/agendar', '/meus-agendamentos', '/meu-cadastro'])
  })

  it('mostra ao profissional a própria agenda, sem gestão da equipe', () => {
    expect(itensVisiveis(profissional).map((i) => i.rota)).toEqual(['/agenda', '/minha-agenda', '/pacientes'])
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
