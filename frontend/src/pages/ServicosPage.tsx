import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import type { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Campo, Input, Interruptor, Select, Textarea } from '@/components/ui/campos'
import { Carregando, Cartao, Selo, Tabela, Td, Th, TituloPagina, TituloSecao, Vazio } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import {
  getServicosEspecialidadesQueryKey,
  getServicosListarQueryKey,
  useServicosAtualizar,
  useServicosCadastrar,
  useServicosCriarEspecialidade,
  useServicosEspecialidades,
  useServicosListar,
} from '@/api/gerado/servicos/servicos'
import { ServicosCadastrarBody } from '@/api/gerado/zod/servicos/servicos.zod'
import type { ServicoResposta } from '@/api/gerado/modelos'
import { usePermissao } from '@/features/sessao/useUsuarioAtual'
import { formatarMoeda, mensagemErro } from '@/lib/formatos'
import { corpoLimpo, NUMERO_OPCIONAL, textoObrigatorio } from '@/lib/formulario'

const POLITICAS = { NENHUM: 'Sem cobrança antecipada', SINAL: 'Sinal', INTEGRAL: 'Pagamento integral' } as const

export function ServicosPage() {
  const podeAdministrar = usePermissao('profissionais:administrar')
  const servicos = useServicosListar({ incluirInativos: true })
  const [editando, setEditando] = useState<ServicoResposta | 'novo' | null>(null)

  return (
    <div className="mx-auto max-w-5xl">
      <TituloPagina
        titulo="Serviços"
        descricao="Tipos de atendimento com duração, preço e cobrança antecipada opcional."
        acoes={podeAdministrar && <Button onClick={() => setEditando('novo')}><Plus aria-hidden="true" /> Novo serviço</Button>}
      />
      {editando && (
        <FormServico servico={editando === 'novo' ? null : editando} aoConcluir={() => setEditando(null)} />
      )}
      {servicos.isPending ? (
        <Carregando />
      ) : !servicos.data?.length ? (
        <Vazio titulo="Nenhum serviço cadastrado" descricao="Cadastre os serviços para liberar o agendamento." />
      ) : (
        <Tabela rotulo="Serviços">
          <thead>
            <tr><Th>Serviço</Th><Th>Duração</Th><Th>Preço</Th><Th>Antecipado</Th><Th>Situação</Th><Th className="w-px" /></tr>
          </thead>
          <tbody>
            {servicos.data.map((s) => (
              <tr key={s.id}>
                <Td className="font-medium">{s.nome}</Td>
                <Td className="numeros-tabulares">{s.duracaoMinutos} min</Td>
                <Td className="numeros-tabulares">{formatarMoeda(s.precoCentavos ?? 0)}</Td>
                <Td>
                  {s.politicaPagamento && POLITICAS[s.politicaPagamento]}
                  {s.valorSinalCentavos != null && <span className="numeros-tabulares"> ({formatarMoeda(s.valorSinalCentavos)})</span>}
                </Td>
                <Td><Selo variante={s.ativo ? 'contorno' : 'tracejado'}>{s.ativo ? 'Ativo' : 'Inativo'}</Selo></Td>
                <Td>
                  {podeAdministrar && (
                    <Button variante="fantasma" tamanho="icone" aria-label={`Editar ${s.nome}`} onClick={() => setEditando(s)}>
                      <Pencil aria-hidden="true" />
                    </Button>
                  )}
                </Td>
              </tr>
            ))}
          </tbody>
        </Tabela>
      )}
      {podeAdministrar && <Especialidades />}
    </div>
  )
}

const SchemaServico = ServicosCadastrarBody.extend({
  nome: textoObrigatorio(120, 'Informe o nome do serviço.'),
}).refine((s) => s.politicaPagamento !== 'SINAL' || s.valorSinalCentavos != null, {
  message: 'Informe o valor do sinal.',
  path: ['valorSinalCentavos'],
})
type DadosServico = z.infer<typeof SchemaServico>

function FormServico({ servico, aoConcluir }: { servico: ServicoResposta | null; aoConcluir: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const cadastrar = useServicosCadastrar()
  const atualizar = useServicosAtualizar()
  const form = useForm<DadosServico>({
    resolver: zodResolver(SchemaServico),
    defaultValues: servico
      ? {
          nome: servico.nome ?? '',
          duracaoMinutos: servico.duracaoMinutos ?? 30,
          precoCentavos: servico.precoCentavos ?? 0,
          politicaPagamento: servico.politicaPagamento ?? 'NENHUM',
          valorSinalCentavos: servico.valorSinalCentavos,
          descricao: servico.descricao ?? '',
          instrucoesPreparo: servico.instrucoesPreparo ?? '',
          ativo: servico.ativo ?? true,
        }
      : { nome: '', duracaoMinutos: 30, precoCentavos: 0, politicaPagamento: 'NENHUM', ativo: true },
  })
  const politica = useWatch({ control: form.control, name: 'politicaPagamento' })
  const { errors } = form.formState
  const numero = { valueAsNumber: true } as const

  const concluir = (mensagem: string) => {
    void queryClient.invalidateQueries({ queryKey: getServicosListarQueryKey({ incluirInativos: true }) })
    toast.sucesso(mensagem)
    aoConcluir()
  }
  const salvar = form.handleSubmit((dados) => {
    const corpo = corpoLimpo({ ...dados, valorSinalCentavos: dados.politicaPagamento === 'SINAL' ? dados.valorSinalCentavos : undefined })
    if (servico?.id) {
      atualizar.mutate({ id: servico.id, data: { ...corpo, versao: servico.versao ?? 0 } },
        { onSuccess: () => concluir('Serviço atualizado.'), onError: (e) => toast.erro(mensagemErro(e)) })
    } else {
      cadastrar.mutate({ data: corpo }, { onSuccess: () => concluir('Serviço cadastrado.'), onError: (e) => toast.erro(mensagemErro(e)) })
    }
  })

  return (
    <Cartao className="mb-6 animate-entrada">
      <TituloSecao>{servico ? `Editar ${servico.nome}` : 'Novo serviço'}</TituloSecao>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-6">
        <Campo rotulo="Nome" obrigatorio erro={errors.nome?.message} className="sm:col-span-3">
          {(p) => <Input {...p} {...form.register('nome')} />}
        </Campo>
        <Campo rotulo="Duração (min)" obrigatorio erro={errors.duracaoMinutos?.message} className="sm:col-span-1">
          {(p) => <Input {...p} type="number" step={5} min={5} max={480} className="numeros-tabulares" {...form.register('duracaoMinutos', numero)} />}
        </Campo>
        <Campo rotulo="Preço (centavos)" obrigatorio erro={errors.precoCentavos?.message} ajuda="Ex.: 25000 = R$ 250,00" className="sm:col-span-2">
          {(p) => <Input {...p} type="number" min={0} className="numeros-tabulares" {...form.register('precoCentavos', numero)} />}
        </Campo>
        <Campo rotulo="Cobrança antecipada" className="sm:col-span-3">
          {(p) => (
            <Select {...p} {...form.register('politicaPagamento')}>
              {Object.entries(POLITICAS).map(([valor, rotulo]) => <option key={valor} value={valor}>{rotulo}</option>)}
            </Select>
          )}
        </Campo>
        {politica === 'SINAL' && (
          <Campo rotulo="Valor do sinal (centavos)" obrigatorio erro={errors.valorSinalCentavos?.message} className="sm:col-span-3">
            {(p) => <Input {...p} type="number" min={1} className="numeros-tabulares" {...form.register('valorSinalCentavos', NUMERO_OPCIONAL)} />}
          </Campo>
        )}
        <Campo rotulo="Descrição" erro={errors.descricao?.message} className="sm:col-span-3">
          {(p) => <Textarea {...p} {...form.register('descricao')} />}
        </Campo>
        <Campo rotulo="Instruções de preparo" erro={errors.instrucoesPreparo?.message} className="sm:col-span-3">
          {(p) => <Textarea {...p} {...form.register('instrucoesPreparo')} />}
        </Campo>
        {servico && <Interruptor rotulo="Ativo" descricao="Inativos não aparecem para agendamento." className="sm:col-span-6" {...form.register('ativo')} />}
        <div className="flex justify-end gap-2 sm:col-span-6">
          <Button type="button" variante="secundario" onClick={aoConcluir}>Cancelar</Button>
          <Button type="submit" disabled={cadastrar.isPending || atualizar.isPending}>Salvar</Button>
        </div>
      </form>
    </Cartao>
  )
}

function Especialidades() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const especialidades = useServicosEspecialidades()
  const criar = useServicosCriarEspecialidade()
  const [nome, setNome] = useState('')

  return (
    <Cartao className="mt-6">
      <TituloSecao>Especialidades</TituloSecao>
      <ul className="mb-4 flex flex-wrap gap-2">
        {especialidades.data?.map((e) => <li key={e.id}><Selo>{e.nome}</Selo></li>)}
        {!especialidades.data?.length && <li className="text-sm text-texto-3">Nenhuma especialidade.</li>}
      </ul>
      <form
        className="flex flex-wrap gap-2"
        onSubmit={(ev) => {
          ev.preventDefault()
          if (!nome.trim()) return
          criar.mutate({ data: { nome } }, {
            onSuccess: () => {
              setNome('')
              void queryClient.invalidateQueries({ queryKey: getServicosEspecialidadesQueryKey() })
              toast.sucesso('Especialidade criada.')
            },
            onError: (e) => toast.erro(mensagemErro(e)),
          })
        }}
      >
        <label htmlFor="nova-especialidade" className="sr-only">Nova especialidade</label>
        <Input id="nova-especialidade" placeholder="Nova especialidade" value={nome} onChange={(e) => setNome(e.target.value)} className="max-w-xs" />
        <Button type="submit" variante="secundario" disabled={criar.isPending}>Adicionar</Button>
      </form>
    </Cartao>
  )
}
