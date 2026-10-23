import { Link, useParams } from 'react-router'
import { ArrowLeft } from 'lucide-react'
import { Carregando, Cartao, Selo, TituloPagina, Vazio } from '@/components/ui/estrutura'
import { useProfissionaisMeu, useProfissionaisObter } from '@/api/gerado/profissionais/profissionais'
import { useServicosEspecialidades, useServicosListar } from '@/api/gerado/servicos/servicos'
import type { ProfissionalResposta } from '@/api/gerado/modelos'
import { Ausencias } from '@/features/profissionais/Ausencias'
import { Expediente } from '@/features/profissionais/Expediente'
import { usePermissao } from '@/features/sessao/useUsuarioAtual'
import { ErroApi } from '@/lib/api'
import { SeloAtivo } from './ProfissionaisPage'

/** Ficha de um profissional vista pela administração/recepção. */
export function ProfissionalPage() {
  const { id = '' } = useParams()
  const profissional = useProfissionaisObter(id, { query: { enabled: Boolean(id) } })
  const podeAdministrar = usePermissao('profissionais:administrar')
  const podeGerirAusencias = usePermissao('ausencias:gerir')

  if (profissional.isPending) return <Carregando />
  if (!profissional.data) return <Vazio titulo="Profissional não encontrado" acao={<Voltar />} />

  return (
    <div className="mx-auto max-w-4xl">
      <Voltar />
      <TituloPagina titulo={profissional.data.nome ?? 'Profissional'} descricao="Registro, expediente e ausências." />
      <div className="flex flex-col gap-6">
        <Resumo profissional={profissional.data} />
        <Expediente profissionalId={id} editavel={podeAdministrar} />
        <Ausencias profissionalId={id} editavel={podeGerirAusencias} />
      </div>
    </div>
  )
}

/** Agenda do próprio profissional logado: ele edita expediente e ausências. */
export function MinhaAgendaPage() {
  const meu = useProfissionaisMeu({ query: { retry: false } })

  if (meu.isPending) return <Carregando />
  if (!meu.data?.id) {
    const semVinculo = meu.error instanceof ErroApi && meu.error.status === 404
    return (
      <div className="mx-auto max-w-4xl">
        <TituloPagina titulo="Minha agenda" />
        <Vazio
          titulo={semVinculo ? 'Seu login ainda não está ligado a um profissional' : 'Não foi possível carregar sua agenda'}
          descricao={semVinculo ? 'Peça à administração para vincular sua conta ao seu cadastro de profissional.' : 'Tente novamente em instantes.'}
        />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-4xl">
      <TituloPagina titulo="Minha agenda" descricao="Seu expediente semanal e suas ausências." />
      <div className="flex flex-col gap-6">
        <Resumo profissional={meu.data} />
        <Expediente profissionalId={meu.data.id} editavel />
        <Ausencias profissionalId={meu.data.id} editavel />
      </div>
    </div>
  )
}

function Voltar() {
  return (
    <Link to="/profissionais" className="mb-4 inline-flex min-h-toque items-center gap-2 text-sm text-texto-2 hover:text-texto">
      <ArrowLeft className="size-4" aria-hidden="true" /> Profissionais
    </Link>
  )
}

function Resumo({ profissional: p }: { profissional: ProfissionalResposta }) {
  const especialidades = useServicosEspecialidades()
  const servicos = useServicosListar()
  const nomes = (ids: string[] | undefined, lista: { id?: string; nome?: string }[] | undefined) =>
    ids?.map((id) => lista?.find((item) => item.id === id)?.nome).filter((nome): nome is string => Boolean(nome)) ?? []

  return (
    <Cartao>
      <dl className="grid gap-4 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-texto-3">Registro</dt>
          <dd className="font-medium numeros-tabulares">{p.conselho} {p.numeroConselho}/{p.ufConselho}</dd>
        </div>
        <div>
          <dt className="text-texto-3">Situação</dt>
          <dd><SeloAtivo ativo={p.ativo ?? false} /></dd>
        </div>
        <div>
          <dt className="text-texto-3">Conta de acesso</dt>
          <dd>{p.usuarioSub ? 'Vinculada' : 'Sem vínculo'}</dd>
        </div>
        <div className="sm:col-span-3">
          <dt className="mb-1 text-texto-3">Especialidades</dt>
          <dd className="flex flex-wrap gap-2">
            {nomes(p.especialidades, especialidades.data).map((n) => <Selo key={n}>{n}</Selo>)}
            {!p.especialidades?.length && '—'}
          </dd>
        </div>
        <div className="sm:col-span-3">
          <dt className="mb-1 text-texto-3">Serviços</dt>
          <dd className="flex flex-wrap gap-2">
            {nomes(p.servicos, servicos.data).map((n) => <Selo key={n}>{n}</Selo>)}
            {!p.servicos?.length && '—'}
          </dd>
        </div>
      </dl>
    </Cartao>
  )
}
