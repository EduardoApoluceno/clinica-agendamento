import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, CalendarPlus, CheckCircle2, Download } from 'lucide-react'
import { useAgendamentosAgendar } from '@/api/gerado/agendamentos/agendamentos'
import { useCatalogoPublicoProfissionais, useCatalogoPublicoServicos } from '@/api/gerado/catalogo-publico/catalogo-publico'
import type { AgendamentoDto, ProfissionalPublico, ServicoPublico } from '@/api/gerado/modelos'
import { Button } from '@/components/ui/button'
import { Campo, Textarea } from '@/components/ui/campos'
import { Carregando, Cartao, Vazio } from '@/components/ui/estrutura'
import { dataDoInstante, diaPorExtenso, faixaHoraria, hora, linkGoogleAgenda } from '@/features/agenda/datas'
import { EscolhaHorario } from '@/features/agenda/EscolhaHorario'
import { useInvalidarAgenda } from '@/features/agenda/useInvalidarAgenda'
import { ErroApi } from '@/lib/api'
import { formatarMoeda, mensagemErro } from '@/lib/formatos'
import { cn } from '@/lib/utils'

const ETAPAS = ['Serviço', 'Profissional', 'Data e horário', 'Confirmação'] as const

/** Paciente / Agendamento online: serviço → profissional → data e horário → confirmação. */
export function AgendarPage() {
  const [etapa, setEtapa] = useState(0)
  const [servico, setServico] = useState<ServicoPublico | null>(null)
  const [profissional, setProfissional] = useState<ProfissionalPublico | null>(null)
  const [horario, setHorario] = useState<{ inicio: string; fim: string } | null>(null)
  const [agendado, setAgendado] = useState<AgendamentoDto | null>(null)

  const recomecar = () => { setEtapa(0); setServico(null); setProfissional(null); setHorario(null); setAgendado(null) }

  if (agendado) return <Sucesso agendamento={agendado} aoAgendarOutra={recomecar} />

  const podeAvancar = [servico, profissional, horario][etapa] != null
  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-1 text-2xl font-semibold">Agendar consulta</h1>
      <p className="mb-6 text-sm text-texto-2">Escolha o serviço, o profissional e o horário. Leva menos de um minuto.</p>
      <Cartao className="rounded-cartao-lg">
        <Progresso etapa={etapa} />
        <div key={etapa} className="mt-6 animate-entrada">
          {etapa === 0 && <EtapaServico valor={servico} aoEscolher={(s) => { setServico(s); setProfissional(null); setHorario(null) }} />}
          {etapa === 1 && servico && (
            <EtapaProfissional servico={servico} valor={profissional}
              aoEscolher={(p) => { setProfissional(p); setHorario(null) }} />
          )}
          {etapa === 2 && servico && profissional && (
            <div className="flex flex-col gap-4">
              <h2 className="text-lg font-semibold">Quando fica melhor?</h2>
              <EscolhaHorario servicoId={servico.id!} profissionalId={profissional.id!} valor={horario?.inicio ?? null}
                aoEscolher={setHorario} paciente />
              <p className="text-sm text-texto-3">Não encontrou um horário? A lista de espera chega em breve.</p>
            </div>
          )}
          {etapa === 3 && servico && profissional && horario && (
            <EtapaConfirmacao servico={servico} profissional={profissional} horario={horario} aoAgendar={setAgendado} />
          )}
        </div>
        <div className="mt-6 flex justify-between gap-2">
          <Button variante="fantasma" tamanho="paciente" className={cn(etapa === 0 && 'invisible')}
            onClick={() => setEtapa((e) => e - 1)}>
            <ArrowLeft aria-hidden="true" /> Voltar
          </Button>
          {etapa < 3 && (
            <Button tamanho="paciente" disabled={!podeAvancar} onClick={() => setEtapa((e) => e + 1)}>Continuar</Button>
          )}
        </div>
      </Cartao>
    </div>
  )
}

function Progresso({ etapa }: { etapa: number }) {
  return (
    <div>
      <div
        role="progressbar"
        aria-label="Progresso do agendamento"
        aria-valuemin={1}
        aria-valuemax={ETAPAS.length}
        aria-valuenow={etapa + 1}
        aria-valuetext={`Etapa ${etapa + 1} de ${ETAPAS.length}: ${ETAPAS[etapa]}`}
        className="h-1.5 overflow-hidden rounded-full bg-superficie-2"
      >
        <div className="h-full rounded-full bg-acao transition-[width] duration-[var(--duracao-lenta)] ease-[var(--ease-suave)]"
          style={{ width: `${((etapa + 1) / ETAPAS.length) * 100}%` }} />
      </div>
      <ol className="mt-3 hidden grid-cols-4 text-xs sm:grid">
        {ETAPAS.map((nome, i) => (
          <li key={nome} className={cn(i <= etapa ? 'font-medium text-texto' : 'text-texto-3')}>{nome}</li>
        ))}
      </ol>
    </div>
  )
}

/** Cartão selecionável com rádio animado. */
function CartaoOpcao({ selecionado, aoEscolher, children }: { selecionado: boolean; aoEscolher: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selecionado}
      onClick={aoEscolher}
      className={cn(
        'flex min-h-toque-paciente w-full items-start gap-3 rounded-cartao border bg-superficie p-4 text-left transition-[border-color,background-color,transform] duration-[var(--duracao-media)] ease-[var(--ease-suave)] active:scale-[0.99]',
        selecionado ? 'border-acao bg-superficie-2' : 'border-borda hover:border-borda-forte',
      )}
    >
      <span aria-hidden="true" className={cn(
        'mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border-2 transition-colors duration-[var(--duracao-media)]',
        selecionado ? 'border-acao' : 'border-borda-forte',
      )}>
        <span className={cn('size-2.5 rounded-full bg-acao transition-transform duration-[var(--duracao-media)] ease-[var(--ease-suave)]',
          selecionado ? 'scale-100' : 'scale-0')} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">{children}</span>
    </button>
  )
}

function EtapaServico({ valor, aoEscolher }: { valor: ServicoPublico | null; aoEscolher: (s: ServicoPublico) => void }) {
  const servicos = useCatalogoPublicoServicos()
  if (servicos.isPending) return <Carregando />
  if (!servicos.data?.length) return <Vazio titulo="Nenhum serviço disponível" descricao="Fale com a recepção." />
  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">Qual atendimento você procura?</h2>
      <div role="radiogroup" aria-label="Serviço" className="flex flex-col gap-2">
        {servicos.data.map((s) => (
          <CartaoOpcao key={s.id} selecionado={valor?.id === s.id} aoEscolher={() => aoEscolher(s)}>
            <span className="flex flex-wrap items-baseline justify-between gap-2">
              <span className="font-medium">{s.nome}</span>
              <span className="numeros-tabulares text-sm">{formatarMoeda(s.precoCentavos ?? 0)}</span>
            </span>
            <span className="text-sm text-texto-2">{s.duracaoMinutos} min{s.descricao ? ` · ${s.descricao}` : ''}</span>
          </CartaoOpcao>
        ))}
      </div>
    </div>
  )
}

function EtapaProfissional({ servico, valor, aoEscolher }: {
  servico: ServicoPublico
  valor: ProfissionalPublico | null
  aoEscolher: (p: ProfissionalPublico) => void
}) {
  const profissionais = useCatalogoPublicoProfissionais()
  if (profissionais.isPending) return <Carregando />
  const atendem = (profissionais.data ?? []).filter((p) => p.servicos?.includes(servico.id!))
  if (!atendem.length) return <Vazio titulo="Nenhum profissional atende este serviço agora" />
  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">Com quem?</h2>
      <div role="radiogroup" aria-label="Profissional" className="flex flex-col gap-2">
        {atendem.map((p) => (
          <CartaoOpcao key={p.id} selecionado={valor?.id === p.id} aoEscolher={() => aoEscolher(p)}>
            <span className="font-medium">{p.nome}</span>
            <span className="text-sm text-texto-2">{[...(p.especialidades ?? []), p.registro].filter(Boolean).join(' · ')}</span>
          </CartaoOpcao>
        ))}
      </div>
    </div>
  )
}

const CADASTRO_PENDENTE = new Set(['cadastro-incompleto', 'celular-nao-verificado'])

function EtapaConfirmacao({ servico, profissional, horario, aoAgendar }: {
  servico: ServicoPublico
  profissional: ProfissionalPublico
  horario: { inicio: string; fim: string }
  aoAgendar: (a: AgendamentoDto) => void
}) {
  const [observacao, setObservacao] = useState('')
  const agendar = useAgendamentosAgendar()
  const invalidar = useInvalidarAgenda()
  const erro = agendar.error
  const precisaCadastro = erro instanceof ErroApi && CADASTRO_PENDENTE.has(erro.problema?.title ?? '')

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">Confira e confirme</h2>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-cartao bg-superficie-2 p-4 text-sm">
        <dt className="text-texto-3">Serviço</dt><dd className="font-medium">{servico.nome}</dd>
        <dt className="text-texto-3">Profissional</dt><dd>{profissional.nome}</dd>
        <dt className="text-texto-3">Quando</dt>
        <dd className="numeros-tabulares">{diaPorExtenso(dataDoInstante(horario.inicio))}, {faixaHoraria(horario.inicio, horario.fim)}</dd>
        <dt className="text-texto-3">Valor</dt><dd className="numeros-tabulares">{formatarMoeda(servico.precoCentavos ?? 0)}, pago na clínica</dd>
      </dl>
      {(servico.valorAntecipadoCentavos ?? 0) > 0 && (
        <p className="rounded-cartao border border-dashed border-borda-forte p-3 text-sm text-texto-2">
          Este serviço terá sinal de {formatarMoeda(servico.valorAntecipadoCentavos!)} via Pix quando o pagamento online
          for liberado. Por enquanto, o horário é reservado sem cobrança.
        </p>
      )}
      {servico.instrucoesPreparo && (
        <p className="text-sm text-texto-2"><span className="font-medium text-texto">Preparo:</span> {servico.instrucoesPreparo}</p>
      )}
      <Campo rotulo="Algo que a clínica deva saber? (opcional)">
        {(props) => <Textarea {...props} maxLength={500} value={observacao} onChange={(e) => setObservacao(e.target.value)} />}
      </Campo>
      {erro != null && (
        <div role="alert" className="rounded-cartao border-2 border-texto p-3 text-sm">
          {mensagemErro(erro)}{' '}
          {precisaCadastro && <Link to="/meu-cadastro" className="font-medium underline">Ir para Meu cadastro</Link>}
        </div>
      )}
      <Button
        tamanho="paciente"
        disabled={agendar.isPending}
        onClick={() => agendar.mutate({
          data: {
            servicoId: servico.id!, profissionalId: profissional.id!, inicio: horario.inicio,
            ...(observacao.trim() ? { observacao: observacao.trim() } : {}),
          },
        }, { onSuccess: (a) => { invalidar(); aoAgendar(a) } })}
      >
        Confirmar agendamento
      </Button>
      <p className="text-xs text-texto-3">Você pode remarcar ou cancelar sem custo em “Meus agendamentos”.</p>
    </div>
  )
}

function Sucesso({ agendamento: a, aoAgendarOutra }: { agendamento: AgendamentoDto; aoAgendarOutra: () => void }) {
  const titulo = `${a.servico} com ${a.profissional}`
  return (
    <div className="mx-auto max-w-2xl">
      <Cartao className="flex animate-entrada flex-col items-center gap-4 rounded-cartao-lg py-10 text-center">
        <CheckCircle2 className="size-12" aria-hidden="true" />
        <div>
          <h1 className="text-2xl font-semibold">Consulta agendada</h1>
          <p className="mt-2 text-texto-2">
            {titulo}<br />
            <span className="numeros-tabulares">{diaPorExtenso(dataDoInstante(a.inicio!))}, às {hora(a.inicio!)}</span>
          </p>
        </div>
        <div className="flex w-full max-w-sm flex-col gap-2">
          <Button asChild tamanho="paciente">
            <a href={linkGoogleAgenda(titulo, a.inicio!, a.fim!, 'Agendado pelo portal da clínica.')} target="_blank" rel="noopener noreferrer">
              <CalendarPlus aria-hidden="true" /> Adicionar ao Google Agenda
            </a>
          </Button>
          <Button asChild variante="secundario" tamanho="paciente">
            <a href={`/api/v1/agendamentos/${a.id}/calendario.ics`} download="consulta.ics">
              <Download aria-hidden="true" /> Baixar para outra agenda (.ics)
            </a>
          </Button>
          <Button asChild variante="fantasma" tamanho="paciente">
            <Link to="/meus-agendamentos">Ver meus agendamentos</Link>
          </Button>
          <Button variante="fantasma" tamanho="paciente" onClick={aoAgendarOutra}>Agendar outra consulta</Button>
        </div>
      </Cartao>
    </div>
  )
}
