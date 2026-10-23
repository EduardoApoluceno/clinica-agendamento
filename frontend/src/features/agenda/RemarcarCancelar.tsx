import { useState } from 'react'
import { CalendarClock, X } from 'lucide-react'
import { useAgendamentosCancelar, useAgendamentosRemarcar } from '@/api/gerado/agendamentos/agendamentos'
import type { AgendamentoDto } from '@/api/gerado/modelos'
import { Button } from '@/components/ui/button'
import { Campo, Textarea } from '@/components/ui/campos'
import { useToast } from '@/components/ui/toast'
import { mensagemErro } from '@/lib/formatos'
import { dataDoInstante, diaPorExtenso, hora, hojeNaClinica } from './datas'
import { EscolhaHorario } from './EscolhaHorario'
import { useInvalidarAgenda } from './useInvalidarAgenda'

interface Props {
  agendamento: AgendamentoDto
  /** Portal do paciente: alvos maiores e textos na 2ª pessoa */
  paciente?: boolean
  aoConcluir?: () => void
}

/** Remarcar (escolhendo outro horário livre) ou cancelar, ambos sem penalidade. */
export function RemarcarCancelar({ agendamento: a, paciente = false, aoConcluir }: Props) {
  const [modo, setModo] = useState<'remarcar' | 'cancelar' | null>(null)
  const [novoInicio, setNovoInicio] = useState<string | null>(null)
  const [motivo, setMotivo] = useState('')
  const remarcar = useAgendamentosRemarcar()
  const cancelar = useAgendamentosCancelar()
  const invalidar = useInvalidarAgenda()
  const toast = useToast()
  const tamanho = paciente ? 'paciente' : 'padrao'

  const concluir = (mensagem: string) => {
    invalidar()
    toast.sucesso(mensagem)
    setModo(null)
    aoConcluir?.()
  }

  if (modo === 'remarcar') {
    return (
      <div className="flex flex-col gap-4">
        <p className="text-sm font-medium">Escolha o novo horário</p>
        <EscolhaHorario
          servicoId={a.servicoId!}
          profissionalId={a.profissionalId!}
          valor={novoInicio}
          aoEscolher={(h) => setNovoInicio(h.inicio)}
          aPartirDe={hojeNaClinica()}
          paciente={paciente}
        />
        <div className="flex flex-wrap gap-2">
          <Button
            tamanho={tamanho}
            disabled={!novoInicio || remarcar.isPending}
            onClick={() => remarcar.mutate({ id: a.id!, data: { inicio: novoInicio!, versao: a.versao ?? 0 } }, {
              onSuccess: () => concluir(`Remarcado para ${diaPorExtenso(dataDoInstante(novoInicio!))}, ${hora(novoInicio!)}.`),
              onError: (e) => toast.erro(mensagemErro(e)),
            })}
          >
            Confirmar novo horário
          </Button>
          <Button variante="secundario" tamanho={tamanho} onClick={() => setModo(null)}>Voltar</Button>
        </div>
      </div>
    )
  }

  if (modo === 'cancelar') {
    return (
      <div className="flex flex-col gap-3">
        <Campo rotulo="Motivo (opcional)">
          {(props) => <Textarea {...props} maxLength={200} value={motivo} onChange={(e) => setMotivo(e.target.value)} />}
        </Campo>
        <div className="flex flex-wrap gap-2">
          <Button
            tamanho={tamanho}
            disabled={cancelar.isPending}
            onClick={() => cancelar.mutate({ id: a.id!, data: motivo.trim() ? { motivo: motivo.trim() } : {} }, {
              onSuccess: () => concluir(paciente ? 'Consulta cancelada. O horário foi liberado.' : 'Agendamento cancelado.'),
              onError: (e) => toast.erro(mensagemErro(e)),
            })}
          >
            Confirmar cancelamento
          </Button>
          <Button variante="secundario" tamanho={tamanho} onClick={() => setModo(null)}>Voltar</Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-wrap gap-2">
      <Button variante="secundario" tamanho={tamanho} onClick={() => setModo('remarcar')}>
        <CalendarClock aria-hidden="true" /> Remarcar
      </Button>
      <Button variante="secundario" tamanho={tamanho} onClick={() => setModo('cancelar')}>
        <X aria-hidden="true" /> Cancelar
      </Button>
    </div>
  )
}
