import { useState } from 'react'
import { Search, X } from 'lucide-react'
import { useAgendamentosAgendar } from '@/api/gerado/agendamentos/agendamentos'
import { useCatalogoPublicoProfissionais, useCatalogoPublicoServicos } from '@/api/gerado/catalogo-publico/catalogo-publico'
import type { ResumoPaciente } from '@/api/gerado/modelos'
import { usePacientesPesquisar } from '@/api/gerado/pacientes/pacientes'
import { Button } from '@/components/ui/button'
import { Campo, Input, Select, Textarea } from '@/components/ui/campos'
import { Cartao } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import { mensagemErro } from '@/lib/formatos'
import { dataDoInstante, diaPorExtenso, hora } from './datas'
import { EscolhaHorario } from './EscolhaHorario'
import { useInvalidarAgenda } from './useInvalidarAgenda'

interface Props {
  /** Pré-seleção vinda de um horário livre da grade */
  profissionalId?: string
  data: string
  inicio?: string
  aoFechar: () => void
}

/** Agendamento pela recepção: paciente → serviço → profissional → horário. */
export function NovoAgendamento({ profissionalId: profissionalInicial, data, inicio: inicioInicial, aoFechar }: Props) {
  const [paciente, setPaciente] = useState<ResumoPaciente | null>(null)
  const [servicoId, setServicoId] = useState('')
  const [profissionalId, setProfissionalId] = useState(profissionalInicial ?? '')
  const [inicio, setInicio] = useState<string | null>(inicioInicial ?? null)
  const [observacao, setObservacao] = useState('')
  const servicos = useCatalogoPublicoServicos()
  const profissionais = useCatalogoPublicoProfissionais()
  const agendar = useAgendamentosAgendar()
  const invalidar = useInvalidarAgenda()
  const toast = useToast()

  const servicosDoProfissional = (servicos.data ?? []).filter((s) => !profissionalId
    || profissionais.data?.find((p) => p.id === profissionalId)?.servicos?.includes(s.id!))
  const profissionaisDoServico = (profissionais.data ?? []).filter((p) => !servicoId || p.servicos?.includes(servicoId))

  return (
    <Cartao className="flex flex-col gap-4" aria-label="Novo agendamento">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Novo agendamento</h2>
        <Button variante="fantasma" tamanho="icone" aria-label="Fechar novo agendamento" onClick={aoFechar}>
          <X aria-hidden="true" />
        </Button>
      </div>

      {paciente ? (
        <div className="flex items-center justify-between gap-2 rounded-botao border border-borda bg-superficie-2 px-3 py-2">
          <div>
            <p className="text-sm font-medium">{paciente.nomeSocial ?? paciente.nome}</p>
            <p className="numeros-tabulares text-xs text-texto-3">CPF {paciente.cpfMascarado}</p>
          </div>
          <Button variante="fantasma" onClick={() => setPaciente(null)}>Trocar</Button>
        </div>
      ) : (
        <BuscaPaciente aoEscolher={setPaciente} />
      )}

      <Campo rotulo="Serviço" obrigatorio>
        {(props) => (
          <Select {...props} value={servicoId} onChange={(e) => { setServicoId(e.target.value); setInicio(null) }}>
            <option value="">Selecione</option>
            {servicosDoProfissional.map((s) => <option key={s.id} value={s.id}>{s.nome} ({s.duracaoMinutos} min)</option>)}
          </Select>
        )}
      </Campo>
      <Campo rotulo="Profissional" obrigatorio>
        {(props) => (
          <Select {...props} value={profissionalId} onChange={(e) => { setProfissionalId(e.target.value); setInicio(null) }}>
            <option value="">Selecione</option>
            {profissionaisDoServico.map((p) => <option key={p.id} value={p.id}>{p.nome}</option>)}
          </Select>
        )}
      </Campo>

      {servicoId && profissionalId && (
        <EscolhaHorario servicoId={servicoId} profissionalId={profissionalId} valor={inicio}
          aoEscolher={(h) => setInicio(h.inicio)} aPartirDe={data} />
      )}

      <Campo rotulo="Observação (opcional)">
        {(props) => <Textarea {...props} maxLength={500} value={observacao} onChange={(e) => setObservacao(e.target.value)} />}
      </Campo>

      <Button
        disabled={!paciente || !servicoId || !profissionalId || !inicio || agendar.isPending}
        onClick={() => agendar.mutate({
          data: {
            servicoId, profissionalId, inicio: inicio!, pacienteId: paciente!.id!,
            ...(observacao.trim() ? { observacao: observacao.trim() } : {}),
          },
        }, {
          onSuccess: () => {
            invalidar()
            toast.sucesso(`Agendado: ${diaPorExtenso(dataDoInstante(inicio!))}, ${hora(inicio!)}.`)
            aoFechar()
          },
          onError: (e) => toast.erro(mensagemErro(e)),
        })}
      >
        Agendar
      </Button>
    </Cartao>
  )
}

function BuscaPaciente({ aoEscolher }: { aoEscolher: (p: ResumoPaciente) => void }) {
  const [termo, setTermo] = useState('')
  const busca = termo.trim()
  const resultado = usePacientesPesquisar({ termo: busca, tamanho: 6 }, { query: { enabled: busca.length >= 3 } })

  return (
    <div className="flex flex-col gap-2">
      <Campo rotulo="Paciente" obrigatorio ajuda="Nome, CPF ou celular (mínimo 3 caracteres).">
        {(props) => (
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-texto-3" />
            <Input {...props} type="search" className="pl-9" value={termo} onChange={(e) => setTermo(e.target.value)} />
          </div>
        )}
      </Campo>
      {busca.length >= 3 && (
        <ul aria-label="Pacientes encontrados" className="flex flex-col gap-1">
          {resultado.data?.itens?.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => aoEscolher(p)}
                className="flex min-h-toque w-full items-center justify-between gap-2 rounded-botao border border-borda px-3 text-left text-sm hover:bg-superficie-2"
              >
                <span className="font-medium">{p.nomeSocial ?? p.nome}</span>
                <span className="numeros-tabulares text-xs text-texto-3">{p.cpfMascarado}</span>
              </button>
            </li>
          ))}
          {resultado.data && !resultado.data.itens?.length && (
            <li className="text-sm text-texto-3">Nenhum paciente encontrado. Cadastre-o em Pacientes.</li>
          )}
        </ul>
      )}
    </div>
  )
}
