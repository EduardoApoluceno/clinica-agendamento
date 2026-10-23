import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useQueryClient } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Campo, Input } from '@/components/ui/campos'
import { Carregando, Cartao, Selo, Tabela, Td, Th, TituloPagina, TituloSecao, Vazio } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import { getPacientesObterQueryKey, usePacientesCadastrar, usePacientesPesquisar } from '@/api/gerado/pacientes/pacientes'
import type { ContatoDto } from '@/api/gerado/modelos'
import { CamposDadosPessoais, DADOS_VAZIOS, SchemaDadosPessoais } from '@/features/pacientes/DadosPessoais'
import { usePermissao } from '@/features/sessao/useUsuarioAtual'
import { formatarData, mascararCelular, mensagemErro } from '@/lib/formatos'
import { celularValido, corpoLimpo } from '@/lib/formulario'

const MINIMO_BUSCA = 3
const TAMANHO_PAGINA = 20

export function PacientesPage() {
  const podeEscrever = usePermissao('pacientes:escrever')
  const [termo, setTermo] = useState('')
  const [busca, setBusca] = useState('')
  const [pagina, setPagina] = useState(0)
  const [novo, setNovo] = useState(false)

  // Espera a pessoa parar de digitar antes de consultar, em vez de uma consulta por tecla.
  useEffect(() => {
    const espera = window.setTimeout(() => {
      setBusca(termo.trim())
      setPagina(0)
    }, 350)
    return () => window.clearTimeout(espera)
  }, [termo])

  const pesquisaAtiva = busca.length >= MINIMO_BUSCA
  const resultado = usePacientesPesquisar(
    { termo: busca, pagina, tamanho: TAMANHO_PAGINA },
    { query: { enabled: pesquisaAtiva, placeholderData: keepPreviousData } },
  )
  const total = resultado.data?.total ?? 0
  const ultimaPagina = Math.max(0, Math.ceil(total / TAMANHO_PAGINA) - 1)

  return (
    <div className="mx-auto max-w-5xl">
      <TituloPagina
        titulo="Pacientes"
        descricao="Busque por nome ou CPF."
        acoes={podeEscrever && !novo && <Button onClick={() => setNovo(true)}><Plus aria-hidden="true" /> Novo paciente</Button>}
      />
      {novo && <FormNovoPaciente aoCancelar={() => setNovo(false)} />}

      <div role="search" className="relative mb-4 max-w-md">
        <label htmlFor="busca-paciente" className="sr-only">Buscar paciente</label>
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-texto-3" aria-hidden="true" />
        <Input id="busca-paciente" type="search" placeholder="Nome ou CPF (mín. 3 caracteres)" className="pl-9"
          value={termo} onChange={(e) => setTermo(e.target.value)} autoComplete="off" />
      </div>

      {!pesquisaAtiva ? (
        <Vazio titulo="Digite para buscar" descricao="Por privacidade, a lista completa de pacientes não é exibida." />
      ) : resultado.isPending ? (
        <Carregando texto="Buscando…" />
      ) : !resultado.data?.itens?.length ? (
        <Vazio titulo="Nenhum paciente encontrado" descricao={`Nada corresponde a “${busca}”.`} />
      ) : (
        <>
          <Tabela rotulo="Resultado da busca de pacientes">
            <thead>
              <tr><Th>Nome</Th><Th>CPF</Th><Th>Nascimento</Th><Th>Conta</Th></tr>
            </thead>
            <tbody>
              {resultado.data.itens.map((p) => (
                <tr key={p.id}>
                  <Td className="font-medium">
                    <Link to={`/pacientes/${p.id}`} className="underline-offset-4 hover:underline">{p.nomeSocial ?? p.nome}</Link>
                    {p.nomeSocial && <span className="block text-xs font-normal text-texto-3">{p.nome}</span>}
                  </Td>
                  <Td className="numeros-tabulares">{p.cpfMascarado}</Td>
                  <Td className="numeros-tabulares">{p.dataNascimento && formatarData(p.dataNascimento)}</Td>
                  <Td>{p.possuiConta ? <Selo>Portal</Selo> : <Selo variante="tracejado">Sem conta</Selo>}</Td>
                </tr>
              ))}
            </tbody>
          </Tabela>
          <nav aria-label="Paginação" className="mt-3 flex items-center justify-between text-sm text-texto-2">
            <span className="numeros-tabulares" aria-live="polite">{total} {total === 1 ? 'paciente' : 'pacientes'}</span>
            <div className="flex gap-2">
              <Button variante="secundario" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>Anterior</Button>
              <Button variante="secundario" disabled={pagina >= ultimaPagina} onClick={() => setPagina((p) => p + 1)}>Próxima</Button>
            </div>
          </nav>
        </>
      )}
    </div>
  )
}

const SchemaNovoPaciente = z
  .object({
    dados: SchemaDadosPessoais,
    celular: z.string().optional(),
    email: z.union([z.literal(''), z.email('E-mail inválido.')]).optional(),
  })
  .refine((p) => !p.celular || celularValido(p.celular), { message: 'Celular inválido. Use DDD + número.', path: ['celular'] })
  .refine((p) => Boolean(p.celular || p.email), { message: 'Informe ao menos um contato.', path: ['celular'] })
type NovoPaciente = z.infer<typeof SchemaNovoPaciente>

/** Contatos no formato da API; o celular é o principal quando existe. */
export function contatosDoFormulario(celular?: string, email?: string): ContatoDto[] {
  const contatos: ContatoDto[] = []
  if (celular) contatos.push({ tipo: 'CELULAR', valor: celular, principal: true })
  if (email) contatos.push({ tipo: 'EMAIL', valor: email, principal: !celular })
  return contatos
}

function FormNovoPaciente({ aoCancelar }: { aoCancelar: () => void }) {
  const toast = useToast()
  const navegar = useNavigate()
  const queryClient = useQueryClient()
  const cadastrar = usePacientesCadastrar()
  const form = useForm<NovoPaciente>({
    resolver: zodResolver(SchemaNovoPaciente),
    defaultValues: { dados: DADOS_VAZIOS, celular: '', email: '' },
  })
  const { errors } = form.formState

  const salvar = form.handleSubmit(({ dados, celular, email }) =>
    cadastrar.mutate(
      { data: { dados: corpoLimpo(dados), contatos: contatosDoFormulario(celular, email) } },
      {
        onSuccess: (paciente) => {
          toast.sucesso('Paciente cadastrado.')
          if (paciente.id) {
            queryClient.setQueryData(getPacientesObterQueryKey(paciente.id), paciente)
            void navegar(`/pacientes/${paciente.id}`)
          }
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    ),
  )

  return (
    <Cartao className="mb-6 animate-entrada">
      <TituloSecao>Novo paciente</TituloSecao>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-6">
        <CamposDadosPessoais register={form.register} errors={errors.dados} />
        <Campo rotulo="Celular" erro={errors.celular?.message} className="sm:col-span-3"
          ajuda="Para o paciente acessar o portal depois, ele precisará confirmar este número ou o e-mail.">
          {(p) => (
            <Input {...p} type="tel" inputMode="tel" autoComplete="off" className="numeros-tabulares"
              {...form.register('celular', { onChange: (e) => { e.target.value = mascararCelular(e.target.value) } })} />
          )}
        </Campo>
        <Campo rotulo="E-mail" erro={errors.email?.message} className="sm:col-span-3">
          {(p) => <Input {...p} type="email" autoComplete="off" {...form.register('email')} />}
        </Campo>
        <div className="flex justify-end gap-2 sm:col-span-6">
          <Button type="button" variante="secundario" onClick={aoCancelar}>Cancelar</Button>
          <Button type="submit" disabled={cadastrar.isPending}>{cadastrar.isPending ? 'Salvando…' : 'Cadastrar'}</Button>
        </div>
      </form>
    </Cartao>
  )
}
