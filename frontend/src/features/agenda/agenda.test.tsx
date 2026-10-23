import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ColunaDto } from '@/api/gerado/modelos'
import { diaCurto, diaPorExtenso, faixaHoraria, hojeNaClinica, linkGoogleAgenda, minutosNoDia, somarDias } from './datas'
import { GradeAgenda } from './GradeAgenda'
import { aguardandoAtendimento, pendente, rotuloStatus } from './status'

describe('datas no fuso da clínica (UTC-4)', () => {
  it('usa a data de Porto Velho, não a do navegador', () => {
    // 02:30 UTC de 24/10 ainda é 22:30 de 23/10 em Porto Velho.
    expect(hojeNaClinica(new Date('2026-10-24T02:30:00Z'))).toBe('2026-10-23')
  })

  it('soma dias atravessando mês e ano', () => {
    expect(somarDias('2026-10-31', 1)).toBe('2026-11-01')
    expect(somarDias('2027-01-01', -1)).toBe('2026-12-31')
  })

  it('formata dia, hora e faixa', () => {
    expect(diaPorExtenso('2026-10-23')).toBe('sexta-feira, 23 de outubro')
    expect(diaCurto('2026-10-23')).toEqual({ semana: 'sex.', dia: '23/10' })
    expect(faixaHoraria('2026-10-23T13:00:00Z', '2026-10-23T13:30:00Z')).toBe('09:00–09:30')
    expect(minutosNoDia('2026-10-23T13:30:00Z')).toBe(9 * 60 + 30)
  })

  it('monta o link do Google Agenda em UTC', () => {
    const url = new URL(linkGoogleAgenda('Consulta', '2026-10-23T13:00:00Z', '2026-10-23T13:30:00Z', 'x'))
    expect(url.searchParams.get('dates')).toBe('20261023T130000Z/20261023T133000Z')
    expect(url.searchParams.get('text')).toBe('Consulta')
  })
})

describe('status', () => {
  it('rótulos e o que ainda pode mudar', () => {
    expect(rotuloStatus('AGUARDANDO_CONFIRMACAO')).toBe('Aguardando confirmação')
    expect(rotuloStatus('desconhecido')).toBe('—')
    expect(pendente('CONFIRMADO')).toBe(true)
    expect(pendente('REALIZADO')).toBe(false)
    expect(aguardandoAtendimento('RESERVADO')).toBe(false)
  })
})

describe('GradeAgenda', () => {
  const colunas: ColunaDto[] = [
    {
      profissionalId: 'p1',
      profissional: 'Dra. Ana',
      expediente: [{ inicio: '2026-10-23T12:00:00Z', fim: '2026-10-23T16:00:00Z' }],
      ausencias: [],
      agendamentos: [{
        id: 'a1', paciente: 'Carla Mendes', servico: 'Consulta', status: 'CONFIRMADO',
        inicio: '2026-10-23T13:00:00Z', fim: '2026-10-23T13:30:00Z',
      }],
      livres: [{ inicio: '2026-10-23T12:00:00Z', fim: '2026-10-23T12:30:00Z' }],
    },
    { profissionalId: 'p2', profissional: 'Dr. Bruno', expediente: [], ausencias: [], agendamentos: [], livres: [] },
  ]

  it('mostra uma coluna por profissional, em ordem de horário, e avisa a seleção', async () => {
    const aoSelecionar = vi.fn()
    render(<GradeAgenda data="2026-10-23" colunas={colunas} selecao={null} aoSelecionar={aoSelecionar} />)

    const agendaAna = screen.getByRole('list', { name: 'Agenda de Dra. Ana' })
    const botoes = Array.from(agendaAna.querySelectorAll('button')).map((b) => b.getAttribute('aria-label'))
    expect(botoes).toEqual(['08:00–08:30, horário livre', '09:00–09:30, Carla Mendes, Consulta, Confirmado'])
    expect(screen.getByRole('list', { name: 'Agenda de Dr. Bruno' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Carla Mendes/ }))
    expect(aoSelecionar).toHaveBeenCalledWith({ tipo: 'agendamento', agendamento: colunas[0]!.agendamentos![0] })
    await userEvent.click(screen.getByRole('button', { name: /horário livre/ }))
    expect(aoSelecionar).toHaveBeenLastCalledWith(expect.objectContaining({ tipo: 'livre', profissionalId: 'p1' }))
  })
})
