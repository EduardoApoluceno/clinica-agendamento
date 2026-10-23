import { useState, type FormEvent } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { Download, ShieldCheck } from 'lucide-react'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Campo, Input } from '@/components/ui/campos'
import { Carregando, Cartao, Selo, TituloPagina, TituloSecao } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import {
  getMeuCadastroConsentimentosQueryKey,
  getMeuCadastroMeuQueryKey,
  meuCadastroExportar,
  useMeuCadastroAtualizar,
  useMeuCadastroCompletar,
  useMeuCadastroConceder,
  useMeuCadastroConfirmarCodigo,
  useMeuCadastroConsentimentos,
  useMeuCadastroMeu,
  useMeuCadastroRevogar,
  useMeuCadastroSolicitarCodigo,
} from '@/api/gerado/meu-cadastro/meu-cadastro'
import type { CodigoEnviado, ConsentimentoDtoFinalidade, PacienteResposta } from '@/api/gerado/modelos'
import { Consentimentos } from '@/features/pacientes/Consentimentos'
import { CamposDadosPessoais, DADOS_VAZIOS, dadosDoPaciente, SchemaDadosPessoais } from '@/features/pacientes/DadosPessoais'
import { ErroApi } from '@/lib/api'
import { formatarDataHora, mascararCelular, mensagemErro } from '@/lib/formatos'
import { celularValido, corpoLimpo, VERSAO_TERMO } from '@/lib/formulario'

/** Área do paciente: completar o cadastro, confirmar o celular, consentimentos e exportação (LGPD). */
export function MeuCadastroPage() {
  const meu = useMeuCadastroMeu({ query: { retry: false } })
  const naoCompletou = meu.error instanceof ErroApi && meu.error.status === 404

  if (meu.isPending) return <Carregando />
  return (
    <div className="mx-auto max-w-3xl">
      <TituloPagina titulo="Meu cadastro" descricao="Seus dados, contatos e autorizações de uso." />
      {naoCompletou ? (
        <CompletarCadastro />
      ) : meu.data ? (
        <div className="flex flex-col gap-6">
          <VerificarCelular paciente={meu.data} />
          <EditarDados paciente={meu.data} />
          <MeusConsentimentos />
          <ExportarDados />
        </div>
      ) : (
        <p role="alert" className="text-sm text-texto-2">{mensagemErro(meu.error)}</p>
      )}
    </div>
  )
}

const SchemaCompletar = z.object({
  dados: SchemaDadosPessoais,
  celular: z.string().refine(celularValido, 'Celular inválido. Use DDD + número.'),
})
type Completar = z.infer<typeof SchemaCompletar>

function CompletarCadastro() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const completar = useMeuCadastroCompletar()
  const form = useForm<Completar>({ resolver: zodResolver(SchemaCompletar), defaultValues: { dados: DADOS_VAZIOS, celular: '' } })
  const { errors } = form.formState

  const salvar = form.handleSubmit(({ dados, celular }) =>
    completar.mutate(
      { data: { dados: corpoLimpo(dados), celular } },
      {
        onSuccess: (paciente) => {
          queryClient.setQueryData(getMeuCadastroMeuQueryKey(), paciente)
          toast.sucesso('Cadastro concluído. Agora confirme seu celular.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    ),
  )

  return (
    <Cartao>
      <TituloSecao>Complete seu cadastro</TituloSecao>
      <p className="mb-4 text-sm text-texto-2">
        Precisamos destes dados para agendar suas consultas. Se você já foi atendido aqui, use o mesmo CPF e
        o celular informado na recepção: assim encontramos seu cadastro.
      </p>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-6">
        <CamposDadosPessoais register={form.register} errors={errors.dados} />
        <Campo rotulo="Celular" obrigatorio erro={errors.celular?.message} className="sm:col-span-3"
          ajuda="Vamos enviar um código para confirmar.">
          {(p) => (
            <Input {...p} type="tel" inputMode="tel" autoComplete="tel-national" className="numeros-tabulares"
              {...form.register('celular', { onChange: (e) => { e.target.value = mascararCelular(e.target.value) } })} />
          )}
        </Campo>
        <div className="flex items-end justify-end sm:col-span-3">
          <Button type="submit" tamanho="paciente" disabled={completar.isPending}>
            {completar.isPending ? 'Salvando…' : 'Concluir cadastro'}
          </Button>
        </div>
      </form>
    </Cartao>
  )
}

function VerificarCelular({ paciente }: { paciente: PacienteResposta }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const solicitar = useMeuCadastroSolicitarCodigo()
  const confirmar = useMeuCadastroConfirmarCodigo()
  const [enviado, setEnviado] = useState<CodigoEnviado | null>(null)
  const [codigo, setCodigo] = useState('')
  const celular = paciente.contatos?.find((c) => c.tipo === 'CELULAR' && c.principal) ?? paciente.contatos?.find((c) => c.tipo === 'CELULAR')

  if (!celular) return null
  if (celular.verificadoEm) {
    return (
      <p className="flex items-center gap-2 text-sm text-texto-2">
        <ShieldCheck className="size-4" aria-hidden="true" />
        Celular {mascararCelular(celular.valor)} confirmado em {formatarDataHora(celular.verificadoEm)}.
      </p>
    )
  }

  const enviar = () =>
    solicitar.mutate(
      { data: { celular: celular.valor } },
      { onSuccess: setEnviado, onError: (e) => toast.erro(mensagemErro(e)) },
    )
  const validar = (ev: FormEvent) => {
    ev.preventDefault()
    confirmar.mutate(
      { data: { codigo } },
      {
        onSuccess: (atualizado) => {
          queryClient.setQueryData(getMeuCadastroMeuQueryKey(), atualizado)
          toast.sucesso('Celular confirmado.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    )
  }

  return (
    <Cartao>
      <TituloSecao>Confirme seu celular</TituloSecao>
      <p className="mb-4 text-sm text-texto-2">
        Enviaremos um código de 6 dígitos para <span className="numeros-tabulares">{mascararCelular(celular.valor)}</span>.
        A confirmação libera lembretes e o agendamento pelo WhatsApp.
      </p>
      {!enviado ? (
        <Button tamanho="paciente" onClick={enviar} disabled={solicitar.isPending}>
          {solicitar.isPending ? 'Enviando…' : 'Enviar código'}
        </Button>
      ) : (
        <form onSubmit={validar} className="flex flex-col gap-3">
          <p className="text-sm text-texto-3" aria-live="polite">
            Código enviado para {enviado.destinoMascarado}
            {enviado.expiraEm && <>; vale até {formatarDataHora(enviado.expiraEm)}</>}.
          </p>
          {enviado.codigoDemo && (
            <p className="text-sm"><Selo variante="tracejado">Demonstração</Selo> Código: <strong className="numeros-tabulares">{enviado.codigoDemo}</strong></p>
          )}
          <div className="flex flex-wrap items-end gap-2">
            <Campo rotulo="Código" className="w-40">
              {(p) => (
                <Input {...p} inputMode="numeric" autoComplete="one-time-code" maxLength={6} className="numeros-tabulares tracking-widest"
                  value={codigo} onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))} />
              )}
            </Campo>
            <Button type="submit" tamanho="paciente" disabled={codigo.length !== 6 || confirmar.isPending}>Confirmar</Button>
            <Button type="button" variante="fantasma" onClick={enviar} disabled={solicitar.isPending}>Reenviar</Button>
          </div>
        </form>
      )}
    </Cartao>
  )
}

const SchemaEdicao = z.object({ dados: SchemaDadosPessoais })
type Edicao = z.infer<typeof SchemaEdicao>

function EditarDados({ paciente }: { paciente: PacienteResposta }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const atualizar = useMeuCadastroAtualizar()
  const form = useForm<Edicao>({ resolver: zodResolver(SchemaEdicao), defaultValues: { dados: dadosDoPaciente(paciente) } })
  const { errors, isDirty } = form.formState

  const salvar = form.handleSubmit(({ dados }) =>
    atualizar.mutate(
      { data: { dados: corpoLimpo(dados), versao: paciente.versao ?? 0 } },
      {
        onSuccess: (salvo) => {
          queryClient.setQueryData(getMeuCadastroMeuQueryKey(), salvo)
          form.reset({ dados: dadosDoPaciente(salvo) })
          toast.sucesso('Dados atualizados.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    ),
  )

  return (
    <Cartao>
      <TituloSecao>Dados pessoais</TituloSecao>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-6">
        <CamposDadosPessoais register={form.register} errors={errors.dados} cpfBloqueado />
        <div className="flex justify-end sm:col-span-6">
          <Button type="submit" disabled={!isDirty || atualizar.isPending}>{atualizar.isPending ? 'Salvando…' : 'Salvar'}</Button>
        </div>
      </form>
    </Cartao>
  )
}

function MeusConsentimentos() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const consentimentos = useMeuCadastroConsentimentos()
  const conceder = useMeuCadastroConceder()
  const revogar = useMeuCadastroRevogar()

  const alternar = (finalidade: ConsentimentoDtoFinalidade, autorizar: boolean) => {
    const opcoes = {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getMeuCadastroConsentimentosQueryKey() })
        toast.sucesso(autorizar ? 'Autorização registrada.' : 'Autorização revogada.')
      },
      onError: (e: unknown) => toast.erro(mensagemErro(e)),
    }
    if (autorizar) conceder.mutate({ data: { finalidade, versaoTermo: VERSAO_TERMO } }, opcoes)
    else revogar.mutate({ finalidade }, opcoes)
  }

  return (
    <Cartao>
      <TituloSecao>Autorizações de uso dos seus dados</TituloSecao>
      <p className="mb-2 text-sm text-texto-3">Você pode mudar de ideia a qualquer momento. Cada mudança fica registrada.</p>
      {consentimentos.isPending ? (
        <Carregando />
      ) : (
        <Consentimentos consentimentos={consentimentos.data ?? []} aoAlternar={alternar}
          ocupado={conceder.isPending || revogar.isPending} />
      )}
    </Cartao>
  )
}

function ExportarDados() {
  const toast = useToast()
  const [baixando, setBaixando] = useState(false)

  const baixar = async () => {
    setBaixando(true)
    try {
      const dados = await meuCadastroExportar()
      const url = URL.createObjectURL(new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' }))
      const link = Object.assign(document.createElement('a'), { href: url, download: 'meus-dados.json' })
      link.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      toast.erro(mensagemErro(e))
    } finally {
      setBaixando(false)
    }
  }

  return (
    <Cartao>
      <TituloSecao>Seus dados</TituloSecao>
      <p className="mb-4 text-sm text-texto-2">Baixe uma cópia de tudo o que a clínica guarda sobre o seu cadastro.</p>
      <Button variante="secundario" onClick={() => void baixar()} disabled={baixando}>
        <Download aria-hidden="true" /> {baixando ? 'Preparando…' : 'Baixar meus dados (JSON)'}
      </Button>
    </Cartao>
  )
}
