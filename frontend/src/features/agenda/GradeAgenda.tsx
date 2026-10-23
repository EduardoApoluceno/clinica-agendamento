import type { CSSProperties } from 'react'
import type { AgendamentoDto, ColunaDto, IntervaloDto } from '@/api/gerado/modelos'
import { cn } from '@/lib/utils'
import { dataDoInstante, faixaHoraria, minutosNoDia } from './datas'
import { formaStatus, rotuloStatus } from './status'

/** 1,6 px por minuto: 30 min = 48 px (alvo de toque confortável). */
const PX_POR_MINUTO = 1.6
const PADRAO = { inicio: 7 * 60, fim: 19 * 60 }

export type Selecao =
  | { tipo: 'agendamento'; agendamento: AgendamentoDto }
  | { tipo: 'livre'; profissionalId: string; profissional: string; inicio: string; fim: string }

interface Props {
  data: string
  colunas: ColunaDto[]
  selecao: Selecao | null
  aoSelecionar: (selecao: Selecao) => void
}

/** Minutos do dia (no fuso da clínica) recortados à data mostrada. */
function recorte(data: string, inicio: string, fim: string): [number, number] {
  const ini = dataDoInstante(inicio) < data ? 0 : minutosNoDia(inicio)
  const fimMin = dataDoInstante(fim) > data ? 24 * 60 : minutosNoDia(fim)
  return [ini, fimMin]
}

function limites(data: string, colunas: ColunaDto[]) {
  const pontos = colunas.flatMap((c) => [...(c.expediente ?? []), ...(c.agendamentos ?? [])])
    .filter((p): p is Required<IntervaloDto> => Boolean(p.inicio && p.fim))
    .map((p) => recorte(data, p.inicio, p.fim))
  if (!pontos.length) return PADRAO
  const inicio = Math.min(...pontos.map(([i]) => i))
  const fim = Math.max(...pontos.map(([, f]) => f))
  return { inicio: Math.floor(inicio / 60) * 60, fim: Math.ceil(fim / 60) * 60 }
}

function posicao(data: string, inicio: string, fim: string, origem: number): CSSProperties {
  const [i, f] = recorte(data, inicio, fim)
  return { top: (i - origem) * PX_POR_MINUTO, height: Math.max((f - i) * PX_POR_MINUTO, 20) }
}

const ESTILO_STATUS = {
  preenchido: 'status-confirmado text-texto',
  tracejado: 'status-aguardando text-texto',
  contorno: 'bg-superficie border border-borda text-texto-3',
} as const

/**
 * Grade do dia: uma coluna por profissional, horários na vertical (ADR-0011, grade própria). Cada
 * coluna é uma lista em ordem de horário, então Tab percorre agendamentos e horários livres na ordem
 * em que aparecem. Rola na horizontal no celular.
 */
export function GradeAgenda({ data, colunas, selecao, aoSelecionar }: Props) {
  const { inicio, fim } = limites(data, colunas)
  const altura = (fim - inicio) * PX_POR_MINUTO
  const horas = Array.from({ length: (fim - inicio) / 60 + 1 }, (_, i) => inicio + i * 60)

  return (
    <div className="overflow-x-auto rounded-cartao border border-borda bg-superficie">
      <div
        className="grid min-w-full"
        style={{ gridTemplateColumns: `56px repeat(${colunas.length}, minmax(180px, 1fr))` }}
      >
        <div className="sticky left-0 z-10 border-b border-borda bg-superficie" />
        {colunas.map((c) => (
          <div key={c.profissionalId} className="border-b border-l border-borda px-3 py-2.5 text-sm font-semibold">
            {c.profissional}
          </div>
        ))}

        <div aria-hidden="true" className="sticky left-0 z-10 bg-superficie" style={{ height: altura }}>
          {horas.map((m) => (
            <span
              key={m}
              className="numeros-tabulares absolute right-2 -translate-y-1/2 text-xs text-texto-3"
              style={{ top: (m - inicio) * PX_POR_MINUTO }}
            >
              {String(Math.floor(m / 60)).padStart(2, '0')}:00
            </span>
          ))}
        </div>

        {colunas.map((c) => (
          <Coluna key={c.profissionalId} data={data} coluna={c} origem={inicio} altura={altura}
            horas={horas} selecao={selecao} aoSelecionar={aoSelecionar} />
        ))}
      </div>
    </div>
  )
}

interface PropsColuna {
  data: string
  coluna: ColunaDto
  origem: number
  altura: number
  horas: number[]
  selecao: Selecao | null
  aoSelecionar: (selecao: Selecao) => void
}

function Coluna({ data, coluna, origem, altura, horas, selecao, aoSelecionar }: PropsColuna) {
  type Item = { inicio: string; fim: string; agendamento?: AgendamentoDto }
  const itens: Item[] = [
    ...(coluna.agendamentos ?? []).filter((a) => a.inicio && a.fim)
      .map((a) => ({ inicio: a.inicio!, fim: a.fim!, agendamento: a })),
    ...(coluna.livres ?? []).filter((l) => l.inicio && l.fim).map((l) => ({ inicio: l.inicio!, fim: l.fim! })),
  ].sort((a, b) => a.inicio.localeCompare(b.inicio))

  return (
    <div
      className="relative border-l border-borda"
      style={{
        height: altura,
        // Fora do expediente: listrado discreto.
        backgroundImage: 'repeating-linear-gradient(135deg, var(--color-superficie-2) 0 6px, transparent 6px 12px)',
      }}
    >
      {horas.map((m) => (
        <div key={m} aria-hidden="true" className="absolute inset-x-0 border-t border-borda"
          style={{ top: (m - origem) * PX_POR_MINUTO }} />
      ))}
      {(coluna.expediente ?? []).map((e) => (
        <div key={e.inicio} aria-hidden="true" className="absolute inset-x-0 bg-superficie"
          style={posicao(data, e.inicio!, e.fim!, origem)} />
      ))}
      {(coluna.ausencias ?? []).map((a) => (
        <div
          key={a.inicio}
          className="absolute inset-x-1 flex items-start rounded-botao border border-dashed border-borda-forte bg-superficie-2 px-2 py-1 text-xs text-texto-2"
          style={posicao(data, a.inicio!, a.fim!, origem)}
        >
          Ausência{a.tipo === 'FERIAS' ? ' (férias)' : ''}
        </div>
      ))}

      <ul aria-label={`Agenda de ${coluna.profissional}`} className="absolute inset-0">
        {itens.map((item) => {
          const estilo = posicao(data, item.inicio, item.fim, origem)
          if (!item.agendamento) {
            const ativo = selecao?.tipo === 'livre' && selecao.inicio === item.inicio
              && selecao.profissionalId === coluna.profissionalId
            return (
              <li key={`livre-${item.inicio}`} className="absolute inset-x-1" style={estilo}>
                <button
                  type="button"
                  aria-label={`${faixaHoraria(item.inicio, item.fim)}, horário livre`}
                  aria-pressed={ativo}
                  onClick={() => aoSelecionar({ tipo: 'livre', profissionalId: coluna.profissionalId!,
                    profissional: coluna.profissional ?? '', inicio: item.inicio, fim: item.fim })}
                  className={cn(
                    'status-livre flex size-full items-center rounded-botao px-2 text-left text-xs text-texto-3 transition-[border-color,color] duration-[var(--duracao-rapida)] hover:border-borda-forte hover:text-texto-2',
                    ativo && 'text-texto ring-2 ring-acao',
                  )}
                >
                  <span className="numeros-tabulares">{faixaHoraria(item.inicio, item.fim)}</span>
                  <span className="ml-auto">Livre</span>
                </button>
              </li>
            )
          }
          const a = item.agendamento
          const ativo = selecao?.tipo === 'agendamento' && selecao.agendamento.id === a.id
          return (
            <li key={a.id} className="absolute inset-x-1 z-[1]" style={estilo}>
              <button
                type="button"
                aria-pressed={ativo}
                aria-label={`${faixaHoraria(a.inicio!, a.fim!)}, ${a.paciente}, ${a.servico}, ${rotuloStatus(a.status)}`}
                onClick={() => aoSelecionar({ tipo: 'agendamento', agendamento: a })}
                className={cn(
                  'flex size-full flex-col overflow-hidden rounded-botao px-2 py-1 text-left text-xs transition-[transform,box-shadow] duration-[var(--duracao-rapida)] active:scale-[0.98]',
                  ESTILO_STATUS[formaStatus(a.status)],
                  a.status === 'PRESENTE' && 'font-semibold',
                  ativo && 'shadow-sutil ring-2 ring-acao',
                )}
              >
                <span className="truncate font-medium">{a.paciente}</span>
                <span className="numeros-tabulares truncate text-texto-2">
                  {faixaHoraria(a.inicio!, a.fim!)} · {a.servico}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

export function Legenda() {
  return (
    <ul aria-label="Legenda" className="flex flex-wrap gap-x-5 gap-y-2 text-xs text-texto-2">
      <li className="flex items-center gap-2"><span aria-hidden="true" className="status-confirmado inline-block h-3.5 w-6 rounded" />Confirmado</li>
      <li className="flex items-center gap-2"><span aria-hidden="true" className="status-aguardando inline-block h-3.5 w-6 rounded" />Aguardando confirmação</li>
      <li className="flex items-center gap-2"><span aria-hidden="true" className="inline-block h-3.5 w-6 rounded border border-borda bg-superficie" />Realizado, falta ou cancelado</li>
      <li className="flex items-center gap-2"><span aria-hidden="true" className="status-livre inline-block h-3.5 w-6 rounded" />Livre</li>
      <li className="flex items-center gap-2"><span aria-hidden="true" className="inline-block h-3.5 w-6 rounded border border-dashed border-borda-forte bg-superficie-2" />Ausência</li>
    </ul>
  )
}
