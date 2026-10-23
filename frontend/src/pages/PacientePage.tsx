import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil } from 'lucide-react'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Campo, Input } from '@/components/ui/campos'
import { Carregando, Cartao, Selo, TituloPagina, TituloSecao, Vazio } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import {
  getPacientesObterQueryKey,
  usePacientesAtualizar,
  usePacientesConsentimentos,
  usePacientesObter,
} from '@/api/gerado/pacientes/pacientes'
import type { ContatoDto, PacienteResposta } from '@/api/gerado/modelos'
import { Consentimentos } from '@/features/pacientes/Consentimentos'
import { CamposDadosPessoais, dadosDoPaciente, SchemaDadosPessoais } from '@/features/pacientes/DadosPessoais'
import { usePermissao } from '@/features/sessao/useUsuarioAtual'
import { formatarData, formatarDataHora, mascararCelular, mensagemErro } from '@/lib/formatos'
import { CANAIS, celularValido, corpoLimpo } from '@/lib/formulario'

const ORIGENS = { PORTAL: 'Portal', WHATSAPP: 'WhatsApp', RECEPCAO: 'Recepção' } as const

export function PacientePage() {
  const { id = '' } = useParams()
  const paciente = usePacientesObter(id, { query: { enabled: Boolean(id) } })
  const consentimentos = usePacientesConsentimentos(id, { query: { enabled: Boolean(id) } })
  const podeEscrever = usePermissao('pacientes:escrever')
  const [editando, setEditando] = useState(false)

  if (paciente.isPending) return <Carregando />
  if (!paciente.data) return <Vazio titulo="Paciente não encontrado" acao={<Voltar />} />
  const p = paciente.data

  return (
    <div className="mx-auto max-w-4xl">
      <Voltar />
      <TituloPagina
        titulo={p.nomeSocial ?? p.nome ?? 'Paciente'}
        {...(p.nomeSocial && p.nome ? { descricao: `Nome civil: ${p.nome}` } : {})}
        acoes={podeEscrever && !editando && (
          <Button variante="secundario" onClick={() => setEditando(true)}><Pencil aria-hidden="true" /> Editar</Button>
        )}
      />
      <div className="flex flex-col gap-6">
        {editando ? <FormEditarPaciente paciente={p} aoConcluir={() => setEditando(false)} /> : <Resumo paciente={p} />}
        <Cartao>
          <TituloSecao>Consentimentos (LGPD)</TituloSecao>
          <p className="mb-2 text-sm text-texto-3">Somente o próprio paciente concede ou revoga, pelo portal.</p>
          {consentimentos.isPending ? <Carregando /> : <Consentimentos consentimentos={consentimentos.data ?? []} />}
        </Cartao>
      </div>
    </div>
  )
}

function Voltar() {
  return (
    <Link to="/pacientes" className="mb-4 inline-flex min-h-toque items-center gap-2 text-sm text-texto-2 hover:text-texto">
      <ArrowLeft className="size-4" aria-hidden="true" /> Pacientes
    </Link>
  )
}

function Resumo({ paciente: p }: { paciente: PacienteResposta }) {
  const item = (rotulo: string, valor: ReactNode) => (
    <div>
      <dt className="text-texto-3">{rotulo}</dt>
      <dd className="font-medium">{valor || '—'}</dd>
    </div>
  )
  return (
    <Cartao>
      <dl className="grid gap-4 text-sm sm:grid-cols-3">
        {item('CPF', <span className="numeros-tabulares">{p.cpf}</span>)}
        {item('Nascimento', p.dataNascimento && <span className="numeros-tabulares">{formatarData(p.dataNascimento)}</span>)}
        {item('Sexo', p.sexo)}
        {item('Prefere avisos por', p.canalPreferido && CANAIS[p.canalPreferido])}
        {item('Origem do cadastro', p.origem && ORIGENS[p.origem])}
        {item('Conta no portal', p.possuiConta ? 'Sim' : 'Não')}
      </dl>
      <h3 className="mt-6 mb-2 text-sm font-semibold">Contatos</h3>
      {!p.contatos?.length ? (
        <p className="text-sm text-texto-3">Nenhum contato.</p>
      ) : (
        <ul className="flex flex-col gap-2 text-sm">
          {p.contatos.map((c) => (
            <li key={`${c.tipo}-${c.valor}`} className="flex flex-wrap items-center gap-2">
              <span className="w-16 text-texto-3">{c.tipo === 'CELULAR' ? 'Celular' : 'E-mail'}</span>
              <span className="numeros-tabulares">{c.tipo === 'CELULAR' ? mascararCelular(c.valor) : c.valor}</span>
              {c.principal && <Selo>Principal</Selo>}
              {c.verificadoEm
                ? <Selo variante="preenchido">Verificado em {formatarDataHora(c.verificadoEm)}</Selo>
                : <Selo variante="tracejado">Não verificado</Selo>}
            </li>
          ))}
        </ul>
      )}
    </Cartao>
  )
}

const SchemaEdicao = z
  .object({
    dados: SchemaDadosPessoais,
    celular: z.string().optional(),
    email: z.union([z.literal(''), z.email('E-mail inválido.')]).optional(),
  })
  .refine((p) => !p.celular || celularValido(p.celular), { message: 'Celular inválido. Use DDD + número.', path: ['celular'] })
type Edicao = z.infer<typeof SchemaEdicao>

/** Contatos acrescentados na edição: entram como adicionais (o principal atual é mantido). */
function novosContatos(celular?: string, email?: string): ContatoDto[] {
  return [
    ...(celular ? [{ tipo: 'CELULAR' as const, valor: celular, principal: false }] : []),
    ...(email ? [{ tipo: 'EMAIL' as const, valor: email, principal: false }] : []),
  ]
}

function FormEditarPaciente({ paciente, aoConcluir }: { paciente: PacienteResposta; aoConcluir: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const atualizar = usePacientesAtualizar()
  const form = useForm<Edicao>({
    resolver: zodResolver(SchemaEdicao),
    defaultValues: { dados: dadosDoPaciente(paciente), celular: '', email: '' },
  })
  const { errors } = form.formState

  const salvar = form.handleSubmit(({ dados, celular, email }) => {
    if (!paciente.id) return
    atualizar.mutate(
      {
        id: paciente.id,
        data: { dados: corpoLimpo(dados), contatos: novosContatos(celular, email), versao: paciente.versao ?? 0 },
      },
      {
        onSuccess: (salvo) => {
          queryClient.setQueryData(getPacientesObterQueryKey(paciente.id ?? ''), salvo)
          toast.sucesso('Cadastro atualizado.')
          aoConcluir()
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    )
  })

  return (
    <Cartao className="animate-entrada">
      <TituloSecao>Editar cadastro</TituloSecao>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-6">
        <CamposDadosPessoais register={form.register} errors={errors.dados} />
        <p className="text-sm text-texto-3 sm:col-span-6">
          Os contatos atuais são mantidos. Preencha abaixo só para acrescentar um novo.
        </p>
        <Campo rotulo="Novo celular" erro={errors.celular?.message} className="sm:col-span-3">
          {(p) => (
            <Input {...p} type="tel" inputMode="tel" autoComplete="off" className="numeros-tabulares"
              {...form.register('celular', { onChange: (e) => { e.target.value = mascararCelular(e.target.value) } })} />
          )}
        </Campo>
        <Campo rotulo="Novo e-mail" erro={errors.email?.message} className="sm:col-span-3">
          {(p) => <Input {...p} type="email" autoComplete="off" {...form.register('email')} />}
        </Campo>
        <div className="flex justify-end gap-2 sm:col-span-6">
          <Button type="button" variante="secundario" onClick={aoConcluir}>Cancelar</Button>
          <Button type="submit" disabled={atualizar.isPending}>{atualizar.isPending ? 'Salvando…' : 'Salvar'}</Button>
        </div>
      </form>
    </Cartao>
  )
}
