import { CalendarPlus, Check, CheckCheck, MessageCircle, UserCheck, UserX } from 'lucide-react'
import {
  useAgendamentosConcluir,
  useAgendamentosConfirmar,
  useAgendamentosFalta,
  useAgendamentosPresenca,
} from '@/api/gerado/agendamentos/agendamentos'
import type { AgendamentoDto } from '@/api/gerado/modelos'
import { Button } from '@/components/ui/button'
import { Cartao, Selo } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import { usePermissao } from '@/features/sessao/useUsuarioAtual'
import { formatarMoeda, mensagemErro } from '@/lib/formatos'
import { dataDoInstante, diaPorExtenso, faixaHoraria, hojeNaClinica } from './datas'
import { RemarcarCancelar } from './RemarcarCancelar'
import { aguardandoAtendimento, formaStatus, pendente, rotuloStatus } from './status'
import { useInvalidarAgenda } from './useInvalidarAgenda'

/**
 * Detalhes e ações do agendamento escolhido na grade.
 * @param aoSair chamado depois de remarcar ou cancelar (o agendamento sai deste horário)
 */
export function PainelAgendamento({ agendamento: a, aoSair }: { agendamento: AgendamentoDto; aoSair: () => void }) {
  const podeEscrever = usePermissao('agenda:escrever')
  const invalidar = useInvalidarAgenda()
  const toast = useToast()
  const confirmar = useAgendamentosConfirmar()
  const presenca = useAgendamentosPresenca()
  const falta = useAgendamentosFalta()
  const concluir = useAgendamentosConcluir()
  const ocupado = confirmar.isPending || presenca.isPending || falta.isPending || concluir.isPending

  const executar = (mutacao: typeof confirmar, mensagem: string) =>
    mutacao.mutate({ id: a.id! }, {
      onSuccess: () => { invalidar(); toast.sucesso(mensagem) },
      onError: (e) => toast.erro(mensagemErro(e)),
    })

  const ehHoje = a.inicio && dataDoInstante(a.inicio) === hojeNaClinica()
  const jaComecou = a.inicio && new Date(a.inicio) <= new Date()

  return (
    <Cartao className="flex flex-col gap-4" aria-label="Detalhes do agendamento">
      <div>
        <Selo variante={formaStatus(a.status)}>{rotuloStatus(a.status)}</Selo>
        <h2 className="mt-3 text-lg font-semibold">{a.paciente}</h2>
        <p className="text-sm text-texto-2">{a.servico} · {a.profissional}</p>
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
        <dt className="text-texto-3">Quando</dt>
        <dd className="numeros-tabulares">
          {a.inicio && <>{diaPorExtenso(dataDoInstante(a.inicio))}, {faixaHoraria(a.inicio, a.fim!)}</>}
        </dd>
        <dt className="text-texto-3">Valor</dt>
        <dd className="numeros-tabulares">{formatarMoeda(a.precoCentavos ?? 0)}</dd>
        <dt className="text-texto-3">Origem</dt>
        <dd>{a.origem === 'PORTAL' ? 'Portal do paciente' : a.origem === 'RECEPCAO' ? 'Recepção' : a.origem}</dd>
        {a.observacao && (<><dt className="text-texto-3">Observação</dt><dd>{a.observacao}</dd></>)}
        {a.motivoCancelamento && (<><dt className="text-texto-3">Motivo</dt><dd>{a.motivoCancelamento}</dd></>)}
      </dl>

      <div className="flex flex-col gap-2">
        {podeEscrever && a.status === 'AGUARDANDO_CONFIRMACAO' && (
          <Button variante="secundario" disabled={ocupado} onClick={() => executar(confirmar, 'Agendamento confirmado.')}>
            <Check aria-hidden="true" /> Confirmar agendamento
          </Button>
        )}
        {aguardandoAtendimento(a.status) && ehHoje && (
          <Button disabled={ocupado} onClick={() => executar(presenca, 'Presença registrada.')}>
            <UserCheck aria-hidden="true" /> Confirmar presença
          </Button>
        )}
        {a.status === 'PRESENTE' && (
          <Button disabled={ocupado} onClick={() => executar(concluir, 'Atendimento concluído.')}>
            <CheckCheck aria-hidden="true" /> Concluir atendimento
          </Button>
        )}
        {aguardandoAtendimento(a.status) && jaComecou && (
          <Button variante="secundario" disabled={ocupado} onClick={() => executar(falta, 'Falta registrada.')}>
            <UserX aria-hidden="true" /> Registrar falta
          </Button>
        )}
        {podeEscrever && pendente(a.status) && (
          <>
            <Button variante="secundario" disabled title="Chega com as notificações (Fase 3)">
              <MessageCircle aria-hidden="true" /> Enviar lembrete no WhatsApp (em breve)
            </Button>
            <RemarcarCancelar key={a.id} agendamento={a} aoConcluir={aoSair} />
          </>
        )}
      </div>
    </Cartao>
  )
}

/** Horário livre escolhido na grade. */
export function PainelHorarioLivre({ profissional, inicio, fim, aoAgendar }: {
  profissional: string
  inicio: string
  fim: string
  aoAgendar: () => void
}) {
  const podeEscrever = usePermissao('agenda:escrever')
  return (
    <Cartao className="flex flex-col gap-4" aria-label="Horário livre">
      <div>
        <Selo variante="tracejado">Livre</Selo>
        <h2 className="mt-3 text-lg font-semibold numeros-tabulares">{faixaHoraria(inicio, fim)}</h2>
        <p className="text-sm text-texto-2">{profissional} · {diaPorExtenso(dataDoInstante(inicio))}</p>
      </div>
      {podeEscrever && (
        <div className="flex flex-col gap-2">
          <Button onClick={aoAgendar}><CalendarPlus aria-hidden="true" /> Agendar neste horário</Button>
          <Button variante="secundario" disabled title="Chega com a lista de espera (Fase 4)">
            Oferecer à lista de espera (em breve)
          </Button>
        </div>
      )}
    </Cartao>
  )
}
