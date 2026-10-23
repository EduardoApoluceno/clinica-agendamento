import { useState } from 'react'
import { Link } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import type { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Campo, Input, Interruptor } from '@/components/ui/campos'
import { Carregando, Cartao, Selo, Tabela, Td, Th, TituloPagina, TituloSecao, Vazio } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import {
  getProfissionaisListarQueryKey,
  getProfissionaisObterQueryKey,
  useProfissionaisAlterarAtivo,
  useProfissionaisAtualizar,
  useProfissionaisCadastrar,
  useProfissionaisListar,
} from '@/api/gerado/profissionais/profissionais'
import { useServicosEspecialidades, useServicosListar } from '@/api/gerado/servicos/servicos'
import { ProfissionaisCadastrarBody } from '@/api/gerado/zod/profissionais/profissionais.zod'
import type { ProfissionalResposta } from '@/api/gerado/modelos'
import { usePermissao } from '@/features/sessao/useUsuarioAtual'
import { mensagemErro } from '@/lib/formatos'
import { corpoLimpo, textoObrigatorio } from '@/lib/formulario'

const FILTRO = { incluirInativos: true }

export function ProfissionaisPage() {
  const podeAdministrar = usePermissao('profissionais:administrar')
  const profissionais = useProfissionaisListar(FILTRO)
  const especialidades = useServicosEspecialidades()
  const [editando, setEditando] = useState<ProfissionalResposta | 'novo' | null>(null)
  const nomeEspecialidade = new Map(especialidades.data?.map((e) => [e.id, e.nome]))

  return (
    <div className="mx-auto max-w-5xl">
      <TituloPagina
        titulo="Profissionais"
        descricao="Equipe que atende, com registro no conselho, especialidades e serviços."
        acoes={podeAdministrar && <Button onClick={() => setEditando('novo')}><Plus aria-hidden="true" /> Novo profissional</Button>}
      />
      {editando && (
        <FormProfissional
          key={editando === 'novo' ? 'novo' : editando.id}
          profissional={editando === 'novo' ? null : editando}
          aoConcluir={() => setEditando(null)}
        />
      )}
      {profissionais.isPending ? (
        <Carregando />
      ) : !profissionais.data?.length ? (
        <Vazio titulo="Nenhum profissional cadastrado" descricao="Cadastre a equipe para montar a agenda." />
      ) : (
        <Tabela rotulo="Profissionais">
          <thead>
            <tr><Th>Nome</Th><Th>Registro</Th><Th>Especialidades</Th><Th>Situação</Th><Th className="w-px" /></tr>
          </thead>
          <tbody>
            {profissionais.data.map((p) => (
              <tr key={p.id}>
                <Td className="font-medium">
                  <Link to={`/profissionais/${p.id}`} className="underline-offset-4 hover:underline">{p.nome}</Link>
                </Td>
                <Td className="numeros-tabulares">{p.conselho} {p.numeroConselho}/{p.ufConselho}</Td>
                <Td className="text-texto-2">
                  {p.especialidades?.map((id) => nomeEspecialidade.get(id)).filter(Boolean).join(', ') || '—'}
                </Td>
                <Td>
                  {podeAdministrar ? <AlternarAtivo profissional={p} /> : <SeloAtivo ativo={p.ativo ?? false} />}
                </Td>
                <Td>
                  {podeAdministrar && (
                    <Button variante="fantasma" tamanho="icone" aria-label={`Editar ${p.nome}`} onClick={() => setEditando(p)}>
                      <Pencil aria-hidden="true" />
                    </Button>
                  )}
                </Td>
              </tr>
            ))}
          </tbody>
        </Tabela>
      )}
    </div>
  )
}

export function SeloAtivo({ ativo }: { ativo: boolean }) {
  return <Selo variante={ativo ? 'contorno' : 'tracejado'}>{ativo ? 'Ativo' : 'Inativo'}</Selo>
}

function AlternarAtivo({ profissional }: { profissional: ProfissionalResposta }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const alterar = useProfissionaisAlterarAtivo()
  const ativo = profissional.ativo ?? false
  return (
    <Interruptor
      rotulo={ativo ? 'Ativo' : 'Inativo'}
      checked={ativo}
      disabled={alterar.isPending || !profissional.id}
      onChange={(e) =>
        profissional.id &&
        alterar.mutate(
          { id: profissional.id, data: { ativo: e.target.checked, versao: profissional.versao ?? 0 } },
          {
            onSuccess: () => void queryClient.invalidateQueries({ queryKey: getProfissionaisListarQueryKey(FILTRO) }),
            onError: (erro) => toast.erro(mensagemErro(erro)),
          },
        )
      }
    />
  )
}

const SchemaProfissional = ProfissionaisCadastrarBody.extend({
  nome: textoObrigatorio(160, 'Informe o nome.'),
  conselho: textoObrigatorio(20, 'Informe o conselho (ex.: CRM, CRP).'),
  numeroConselho: textoObrigatorio(20, 'Informe o número do registro.'),
  ufConselho: textoObrigatorio(2, 'Informe a UF.'),
})
type DadosProfissional = z.infer<typeof SchemaProfissional>

function FormProfissional({ profissional, aoConcluir }: { profissional: ProfissionalResposta | null; aoConcluir: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const especialidades = useServicosEspecialidades()
  const servicos = useServicosListar()
  const cadastrar = useProfissionaisCadastrar()
  const atualizar = useProfissionaisAtualizar()
  const form = useForm<DadosProfissional>({
    resolver: zodResolver(SchemaProfissional),
    defaultValues: {
      nome: profissional?.nome ?? '',
      conselho: profissional?.conselho ?? '',
      numeroConselho: profissional?.numeroConselho ?? '',
      ufConselho: profissional?.ufConselho ?? '',
      usuarioSub: profissional?.usuarioSub ?? '',
      especialidades: profissional?.especialidades ?? [],
      servicos: profissional?.servicos ?? [],
    },
  })
  const { errors } = form.formState

  const concluir = (mensagem: string, salvo: ProfissionalResposta) => {
    void queryClient.invalidateQueries({ queryKey: getProfissionaisListarQueryKey(FILTRO) })
    if (salvo.id) queryClient.setQueryData(getProfissionaisObterQueryKey(salvo.id), salvo)
    toast.sucesso(mensagem)
    aoConcluir()
  }
  const salvar = form.handleSubmit((dados) => {
    const corpo = corpoLimpo({ ...dados, ufConselho: dados.ufConselho.toUpperCase() })
    const tratarErro = { onError: (e: unknown) => toast.erro(mensagemErro(e)) }
    if (profissional?.id) {
      atualizar.mutate({ id: profissional.id, data: { ...corpo, versao: profissional.versao ?? 0 } },
        { onSuccess: (p) => concluir('Profissional atualizado.', p), ...tratarErro })
    } else {
      cadastrar.mutate({ data: corpo }, { onSuccess: (p) => concluir('Profissional cadastrado.', p), ...tratarErro })
    }
  })

  return (
    <Cartao className="mb-6 animate-entrada">
      <TituloSecao>{profissional ? `Editar ${profissional.nome}` : 'Novo profissional'}</TituloSecao>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-6">
        <Campo rotulo="Nome" obrigatorio erro={errors.nome?.message} className="sm:col-span-6">
          {(p) => <Input {...p} autoComplete="off" {...form.register('nome')} />}
        </Campo>
        <Campo rotulo="Conselho" obrigatorio erro={errors.conselho?.message} className="sm:col-span-2">
          {(p) => <Input {...p} placeholder="CRM" className="uppercase" {...form.register('conselho')} />}
        </Campo>
        <Campo rotulo="Número" obrigatorio erro={errors.numeroConselho?.message} className="sm:col-span-2">
          {(p) => <Input {...p} className="numeros-tabulares" {...form.register('numeroConselho')} />}
        </Campo>
        <Campo rotulo="UF" obrigatorio erro={errors.ufConselho?.message} className="sm:col-span-2">
          {(p) => <Input {...p} maxLength={2} className="uppercase" {...form.register('ufConselho')} />}
        </Campo>
        <Campo rotulo="Conta de acesso (ID no Keycloak)" erro={errors.usuarioSub?.message} className="sm:col-span-6"
          ajuda="Liga o profissional ao login dele, para que ele gerencie a própria agenda. Opcional.">
          {(p) => <Input {...p} autoComplete="off" spellCheck={false} {...form.register('usuarioSub')} />}
        </Campo>
        <Marcadores rotulo="Especialidades" className="sm:col-span-3"
          opcoes={especialidades.data?.map((e) => ({ id: e.id ?? '', nome: e.nome ?? '' })) ?? []}
          registrar={form.register('especialidades')} />
        <Marcadores rotulo="Serviços que atende" className="sm:col-span-3"
          opcoes={servicos.data?.map((s) => ({ id: s.id ?? '', nome: s.nome ?? '' })) ?? []}
          registrar={form.register('servicos')} />
        <div className="flex justify-end gap-2 sm:col-span-6">
          <Button type="button" variante="secundario" onClick={aoConcluir}>Cancelar</Button>
          <Button type="submit" disabled={cadastrar.isPending || atualizar.isPending}>Salvar</Button>
        </div>
      </form>
    </Cartao>
  )
}

/** Grupo de caixas de seleção ligado a um campo-lista do react-hook-form. */
function Marcadores({ rotulo, opcoes, registrar, className }: {
  rotulo: string
  opcoes: { id: string; nome: string }[]
  registrar: ReturnType<ReturnType<typeof useForm<DadosProfissional>>['register']>
  className?: string
}) {
  return (
    <fieldset className={className}>
      <legend className="mb-2 text-sm font-medium">{rotulo}</legend>
      {!opcoes.length && <p className="text-sm text-texto-3">Nenhum item cadastrado.</p>}
      <div className="flex flex-col gap-1">
        {opcoes.map((o) => (
          <label key={o.id} className="flex min-h-toque items-center gap-2 text-sm">
            <input type="checkbox" value={o.id} className="size-4 accent-acao" {...registrar} />
            {o.nome}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
