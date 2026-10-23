import { Link } from 'react-router'
import { CalendarPlus, Check } from 'lucide-react'
import { useAgendamentosConfirmar, useAgendamentosMeus } from '@/api/gerado/agendamentos/agendamentos'
import type { AgendamentoDto } from '@/api/gerado/modelos'
import { Button } from '@/components/ui/button'
import { Carregando, Cartao, Selo, TituloPagina, TituloSecao, Vazio } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import { dataDoInstante, diaPorExtenso, faixaHoraria } from '@/features/agenda/datas'
import { RemarcarCancelar } from '@/features/agenda/RemarcarCancelar'
import { formaStatus, pendente, rotuloStatus } from '@/features/agenda/status'
import { useInvalidarAgenda } from '@/features/agenda/useInvalidarAgenda'
import { mensagemErro } from '@/lib/formatos'

/** Portal do paciente: próximos agendamentos (confirmar, remarcar, cancelar) e histórico. */
export function MeusAgendamentosPage() {
  const meus = useAgendamentosMeus()
  const agora = new Date().toISOString()
  const proximos = (meus.data ?? []).filter((a) => pendente(a.status) && (a.fim ?? '') > agora)
    .sort((a, b) => (a.inicio ?? '').localeCompare(b.inicio ?? ''))
  const anteriores = (meus.data ?? []).filter((a) => !proximos.includes(a))

  return (
    <div className="mx-auto max-w-2xl">
      <TituloPagina
        titulo="Meus agendamentos"
        acoes={<Button asChild tamanho="paciente"><Link to="/agendar"><CalendarPlus aria-hidden="true" /> Agendar consulta</Link></Button>}
      />
      {meus.isPending ? (
        <Carregando />
      ) : (
        <div className="flex flex-col gap-8">
          <section>
            <TituloSecao>Próximos</TituloSecao>
            {proximos.length ? (
              <ul className="flex flex-col gap-3">
                {proximos.map((a) => <li key={a.id}><Proximo agendamento={a} /></li>)}
              </ul>
            ) : (
              <Vazio titulo="Nenhuma consulta marcada" descricao="Agende pelo botão acima, em poucos passos." />
            )}
          </section>
          {anteriores.length > 0 && (
            <section>
              <TituloSecao>Anteriores e cancelados</TituloSecao>
              <ul className="flex flex-col divide-y divide-borda rounded-cartao border border-borda bg-superficie">
                {anteriores.map((a) => (
                  <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
                    <span>
                      <span className="font-medium">{a.servico}</span> · {a.profissional}
                      <span className="numeros-tabulares block text-texto-3">
                        {diaPorExtenso(dataDoInstante(a.inicio!))}, {faixaHoraria(a.inicio!, a.fim!)}
                      </span>
                    </span>
                    <Selo variante={formaStatus(a.status)}>{rotuloStatus(a.status)}</Selo>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </div>
  )
}

function Proximo({ agendamento: a }: { agendamento: AgendamentoDto }) {
  const confirmar = useAgendamentosConfirmar()
  const invalidar = useInvalidarAgenda()
  const toast = useToast()
  return (
    <Cartao className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-semibold">{a.servico}</p>
          <p className="text-sm text-texto-2">{a.profissional}</p>
          <p className="numeros-tabulares mt-1 text-sm">{diaPorExtenso(dataDoInstante(a.inicio!))}, {faixaHoraria(a.inicio!, a.fim!)}</p>
        </div>
        <Selo variante={formaStatus(a.status)}>{rotuloStatus(a.status)}</Selo>
      </div>
      {a.status === 'AGUARDANDO_CONFIRMACAO' && (
        <Button
          tamanho="paciente"
          disabled={confirmar.isPending}
          onClick={() => confirmar.mutate({ id: a.id! }, {
            onSuccess: () => { invalidar(); toast.sucesso('Presença confirmada. Até lá!') },
            onError: (e) => toast.erro(mensagemErro(e)),
          })}
        >
          <Check aria-hidden="true" /> Confirmar que vou
        </Button>
      )}
      <RemarcarCancelar agendamento={a} paciente />
    </Cartao>
  )
}
