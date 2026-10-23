import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getDisponibilidadeHorariosQueryKey, useDisponibilidadeHorarios } from '@/api/gerado/disponibilidade/disponibilidade'
import type { HorarioDto } from '@/api/gerado/modelos'
import { Carregando } from '@/components/ui/estrutura'
import { cn } from '@/lib/utils'
import { dataDoInstante, diaCurto, hojeNaClinica, hora, somarDias } from './datas'
import { useAvisosAgenda } from './useAvisosAgenda'

const DIAS_VISIVEIS = 14

interface Props {
  servicoId: string
  profissionalId: string
  /** Início escolhido (ISO) */
  valor: string | null
  aoEscolher: (horario: Required<Pick<HorarioDto, 'inicio' | 'fim'>>) => void
  /** Primeiro dia mostrado (padrão: hoje) */
  aPartirDe?: string
  /** Alvos de toque maiores no portal do paciente */
  paciente?: boolean
}

/**
 * Dias em chips horizontais e horários em grade; indisponíveis aparecem riscados. Atualiza sozinho
 * quando alguém ocupa ou libera um horário do profissional (SSE).
 */
export function EscolhaHorario({ servicoId, profissionalId, valor, aoEscolher, aPartirDe, paciente = false }: Props) {
  const inicio = aPartirDe ?? hojeNaClinica()
  const params = { servicoId, profissionalId, de: inicio, ate: somarDias(inicio, DIAS_VISIVEIS - 1) }
  const horarios = useDisponibilidadeHorarios(params)
  const queryClient = useQueryClient()
  useAvisosAgenda(() => void queryClient.invalidateQueries({ queryKey: getDisponibilidadeHorariosQueryKey(params) }),
    profissionalId)

  const porDia = useMemo(() => {
    const mapa = new Map<string, HorarioDto[]>()
    for (let i = 0; i < DIAS_VISIVEIS; i++) mapa.set(somarDias(inicio, i), [])
    for (const h of horarios.data ?? []) {
      if (h.inicio) mapa.get(dataDoInstante(h.inicio))?.push(h)
    }
    return mapa
  }, [horarios.data, inicio])

  const primeiroComVaga = [...porDia.entries()].find(([, lista]) => lista.some((h) => h.livre))?.[0]
  const [diaEscolhido, setDiaEscolhido] = useState<string | null>(valor ? dataDoInstante(valor) : null)
  const dia = diaEscolhido ?? primeiroComVaga ?? inicio
  const doDia = porDia.get(dia) ?? []

  if (horarios.isPending) return <Carregando texto="Buscando horários…" />
  if (horarios.isError) return <p role="alert" className="text-sm text-texto-2">Não foi possível carregar os horários.</p>

  return (
    <div className="flex flex-col gap-4">
      <div role="radiogroup" aria-label="Dia" className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
        {[...porDia.entries()].map(([d, lista]) => {
          const livres = lista.filter((h) => h.livre).length
          const { semana, dia: diaMes } = diaCurto(d)
          const ativo = d === dia
          return (
            <button
              key={d}
              type="button"
              role="radio"
              aria-checked={ativo}
              aria-label={`${semana} ${diaMes}, ${livres ? `${livres} horários livres` : 'sem horários'}`}
              disabled={!livres}
              onClick={() => setDiaEscolhido(d)}
              className={cn(
                'flex shrink-0 flex-col items-center rounded-botao border px-3 py-2 text-sm transition-[background-color,border-color,transform] duration-[var(--duracao-rapida)] active:scale-[0.97]',
                paciente ? 'min-h-toque-paciente min-w-16' : 'min-h-toque min-w-14',
                ativo ? 'border-acao bg-acao text-acao-texto' : 'border-borda bg-superficie hover:border-borda-forte',
                !livres && 'border-dashed text-texto-3 opacity-60',
              )}
            >
              <span className="text-xs capitalize">{semana}</span>
              <span className="numeros-tabulares font-semibold">{diaMes}</span>
            </button>
          )
        })}
      </div>

      {doDia.length === 0 ? (
        <p className="text-sm text-texto-3">Sem atendimento neste dia.</p>
      ) : (
        <div role="radiogroup" aria-label="Horário" className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6">
          {doDia.map((h) => {
            const escolhido = h.inicio === valor
            return (
              <button
                key={h.inicio}
                type="button"
                role="radio"
                aria-checked={escolhido}
                disabled={!h.livre}
                aria-label={h.livre ? hora(h.inicio!) : `${hora(h.inicio!)}, indisponível`}
                onClick={() => aoEscolher({ inicio: h.inicio!, fim: h.fim! })}
                className={cn(
                  'numeros-tabulares rounded-botao border text-sm font-medium transition-[background-color,border-color,transform] duration-[var(--duracao-rapida)] active:scale-[0.97]',
                  paciente ? 'min-h-toque-paciente' : 'min-h-toque',
                  escolhido ? 'border-acao bg-acao text-acao-texto' : 'border-borda bg-superficie hover:border-borda-forte',
                  !h.livre && 'cursor-not-allowed border-dashed text-texto-3 line-through',
                )}
              >
                {hora(h.inicio!)}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
