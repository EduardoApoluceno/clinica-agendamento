import { useState } from 'react'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react'
import { useAgendaDiaDia } from '@/api/gerado/agenda-dia/agenda-dia'
import { Button } from '@/components/ui/button'
import { Carregando, Cartao, TituloPagina, Vazio } from '@/components/ui/estrutura'
import { diaPorExtenso, hojeNaClinica, somarDias } from '@/features/agenda/datas'
import { GradeAgenda, Legenda, type Selecao } from '@/features/agenda/GradeAgenda'
import { NovoAgendamento } from '@/features/agenda/NovoAgendamento'
import { PainelAgendamento, PainelHorarioLivre } from '@/features/agenda/PainelAgendamento'
import { useAvisosAgenda } from '@/features/agenda/useAvisosAgenda'
import { useInvalidarAgenda } from '@/features/agenda/useInvalidarAgenda'
import { usePermissao } from '@/features/sessao/useUsuarioAtual'

type Painel = Selecao | { tipo: 'novo'; profissionalId?: string; inicio?: string } | null

/** Recepção / Agenda do dia: resumo, navegação de dias, grade por profissional e painel de detalhes. */
export function AgendaDiaPage() {
  const [data, setData] = useState(hojeNaClinica)
  const [painel, setPainel] = useState<Painel>(null)
  const podeEscrever = usePermissao('agenda:escrever')
  const agenda = useAgendaDiaDia({ data })
  const invalidar = useInvalidarAgenda()
  useAvisosAgenda(invalidar)

  const irPara = (novaData: string) => { setData(novaData); setPainel(null) }
  const hoje = hojeNaClinica()
  // O painel mostra a versão mais recente do agendamento escolhido (depois de confirmar, remarcar...).
  const agendamentoAtual = painel?.tipo === 'agendamento'
    ? agenda.data?.colunas?.flatMap((c) => c.agendamentos ?? []).find((a) => a.id === painel.agendamento.id)
      ?? painel.agendamento
    : undefined

  return (
    <div className="mx-auto max-w-7xl">
      <TituloPagina
        titulo="Agenda do dia"
        descricao={diaPorExtenso(data)}
        acoes={
          <>
            <div role="group" aria-label="Navegar entre dias" className="flex gap-1">
              <Button variante="secundario" tamanho="icone" aria-label="Dia anterior" onClick={() => irPara(somarDias(data, -1))}>
                <ChevronLeft aria-hidden="true" />
              </Button>
              <Button variante="secundario" disabled={data === hoje} onClick={() => irPara(hoje)}>Hoje</Button>
              <Button variante="secundario" tamanho="icone" aria-label="Próximo dia" onClick={() => irPara(somarDias(data, 1))}>
                <ChevronRight aria-hidden="true" />
              </Button>
            </div>
            {podeEscrever && (
              <Button onClick={() => setPainel({ tipo: 'novo' })}><Plus aria-hidden="true" /> Novo agendamento</Button>
            )}
          </>
        }
      />

      {agenda.data?.resumo && (
        <dl aria-label="Resumo do dia" className="mb-5 grid grid-cols-3 gap-3 sm:max-w-xl">
          <Indicador rotulo="Agendados" valor={agenda.data.resumo.agendados} />
          <Indicador rotulo="Aguardando confirmação" valor={agenda.data.resumo.aguardandoConfirmacao} />
          <Indicador rotulo="Horários livres" valor={agenda.data.resumo.livres} />
        </dl>
      )}

      <div className="flex flex-col gap-5 lg:grid lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
        <div className="flex min-w-0 flex-col gap-3">
          {agenda.isPending ? (
            <Carregando />
          ) : !agenda.data?.colunas?.length ? (
            <Vazio titulo="Nenhum profissional com agenda" descricao="Cadastre profissionais e o expediente deles para montar a grade." />
          ) : (
            <GradeAgenda data={data} colunas={agenda.data.colunas}
              selecao={painel?.tipo === 'novo' ? null : painel} aoSelecionar={setPainel} />
          )}
          <Legenda />
        </div>

        <aside aria-label="Painel" className="lg:sticky lg:top-6">
          {painel === null && (
            <Cartao className="text-sm text-texto-3">Escolha um agendamento ou um horário livre na grade.</Cartao>
          )}
          {agendamentoAtual && <PainelAgendamento key={agendamentoAtual.id} agendamento={agendamentoAtual} aoSair={() => setPainel(null)} />}
          {painel?.tipo === 'livre' && (
            <PainelHorarioLivre
              profissional={painel.profissional}
              inicio={painel.inicio}
              fim={painel.fim}
              aoAgendar={() => setPainel({ tipo: 'novo', profissionalId: painel.profissionalId, inicio: painel.inicio })}
            />
          )}
          {painel?.tipo === 'novo' && (
            <NovoAgendamento
              key={`${painel.profissionalId}-${painel.inicio}`}
              data={data}
              {...(painel.profissionalId ? { profissionalId: painel.profissionalId } : {})}
              {...(painel.inicio ? { inicio: painel.inicio } : {})}
              aoFechar={() => setPainel(null)}
            />
          )}
        </aside>
      </div>
    </div>
  )
}

function Indicador({ rotulo, valor }: { rotulo: string; valor: number | undefined }) {
  return (
    <div className="rounded-cartao border border-borda bg-superficie px-4 py-3">
      <dt className="text-xs text-texto-3">{rotulo}</dt>
      <dd className="numeros-tabulares text-2xl font-semibold">{valor ?? 0}</dd>
    </div>
  )
}
