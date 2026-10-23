import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import type { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Campo, Input, Interruptor } from '@/components/ui/campos'
import { Carregando, Cartao, TituloPagina, TituloSecao } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import { getClinicaObterQueryKey, useClinicaAtualizar, useClinicaObter } from '@/api/gerado/clinica/clinica'
import {
  getConfiguracaoObterQueryKey,
  useConfiguracaoAtualizar,
  useConfiguracaoObter,
} from '@/api/gerado/configuracao/configuracao'
import { ClinicaAtualizarBody } from '@/api/gerado/zod/clinica/clinica.zod'
import { ConfiguracaoAtualizarBody } from '@/api/gerado/zod/configuracao/configuracao.zod'
import type { ClinicaResposta, ConfiguracaoDto } from '@/api/gerado/modelos'
import { mensagemErro } from '@/lib/formatos'
import { corpoLimpo } from '@/lib/formulario'

export function ConfiguracoesPage() {
  const clinica = useClinicaObter()
  const configuracao = useConfiguracaoObter()
  return (
    <div className="mx-auto max-w-4xl">
      <TituloPagina titulo="Configurações" descricao="Dados da clínica e regras da agenda." />
      <div className="flex flex-col gap-6">
        {clinica.data ? <FormDadosClinica clinica={clinica.data} /> : <Carregando />}
        {configuracao.data ? <FormRegrasAgenda configuracao={configuracao.data} /> : <Carregando />}
      </div>
    </div>
  )
}

type DadosClinica = z.infer<typeof ClinicaAtualizarBody>

function FormDadosClinica({ clinica }: { clinica: ClinicaResposta }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const atualizar = useClinicaAtualizar()
  const form = useForm<DadosClinica>({ resolver: zodResolver(ClinicaAtualizarBody), defaultValues: paraFormulario(clinica) })
  const { errors } = form.formState

  useEffect(() => form.reset(paraFormulario(clinica)), [clinica, form])

  const salvar = form.handleSubmit((dados) =>
    atualizar.mutate(
      { data: corpoLimpo(dados) },
      {
        onSuccess: (salva) => {
          queryClient.setQueryData(getClinicaObterQueryKey(), salva)
          toast.sucesso('Dados da clínica salvos.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    ),
  )

  return (
    <Cartao>
      <TituloSecao>Dados da clínica</TituloSecao>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-6">
        <Campo rotulo="Nome" obrigatorio erro={errors.nome?.message} className="sm:col-span-4">
          {(p) => <Input {...p} {...form.register('nome')} />}
        </Campo>
        <Campo rotulo="CNPJ" obrigatorio erro={errors.cnpj?.message} className="sm:col-span-2">
          {(p) => <Input {...p} inputMode="numeric" {...form.register('cnpj')} />}
        </Campo>
        <Campo rotulo="Logradouro" obrigatorio erro={errors.endereco?.logradouro?.message} className="sm:col-span-4">
          {(p) => <Input {...p} {...form.register('endereco.logradouro')} />}
        </Campo>
        <Campo rotulo="Número" obrigatorio erro={errors.endereco?.numero?.message} className="sm:col-span-2">
          {(p) => <Input {...p} {...form.register('endereco.numero')} />}
        </Campo>
        <Campo rotulo="Complemento" erro={errors.endereco?.complemento?.message} className="sm:col-span-2">
          {(p) => <Input {...p} {...form.register('endereco.complemento')} />}
        </Campo>
        <Campo rotulo="Bairro" obrigatorio erro={errors.endereco?.bairro?.message} className="sm:col-span-2">
          {(p) => <Input {...p} {...form.register('endereco.bairro')} />}
        </Campo>
        <Campo rotulo="CEP" obrigatorio erro={errors.endereco?.cep?.message} className="sm:col-span-2">
          {(p) => <Input {...p} inputMode="numeric" {...form.register('endereco.cep')} />}
        </Campo>
        <Campo rotulo="Cidade" obrigatorio erro={errors.endereco?.cidade?.message} className="sm:col-span-3">
          {(p) => <Input {...p} {...form.register('endereco.cidade')} />}
        </Campo>
        <Campo rotulo="UF" obrigatorio erro={errors.endereco?.uf?.message} className="sm:col-span-1">
          {(p) => <Input {...p} maxLength={2} className="uppercase" {...form.register('endereco.uf')} />}
        </Campo>
        <Campo rotulo="Telefone" erro={errors.telefone?.message} className="sm:col-span-2">
          {(p) => <Input {...p} inputMode="tel" {...form.register('telefone')} />}
        </Campo>
        <div className="flex justify-end sm:col-span-6">
          <Button type="submit" disabled={atualizar.isPending}>
            {atualizar.isPending ? 'Salvando…' : 'Salvar dados'}
          </Button>
        </div>
      </form>
    </Cartao>
  )
}

function paraFormulario(c: ClinicaResposta): DadosClinica {
  const e = c.endereco
  return {
    nome: c.nome ?? '',
    cnpj: c.cnpj ?? '',
    telefone: c.telefone ?? '',
    versao: c.versao ?? 0,
    endereco: {
      logradouro: e?.logradouro ?? '',
      numero: e?.numero ?? '',
      complemento: e?.complemento ?? '',
      bairro: e?.bairro ?? '',
      cidade: e?.cidade ?? '',
      uf: e?.uf ?? '',
      cep: e?.cep ?? '',
    },
  }
}

type RegrasAgenda = z.infer<typeof ConfiguracaoAtualizarBody>

function FormRegrasAgenda({ configuracao }: { configuracao: ConfiguracaoDto }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const atualizar = useConfiguracaoAtualizar()
  const form = useForm<RegrasAgenda>({ resolver: zodResolver(ConfiguracaoAtualizarBody), defaultValues: configuracao })
  const { errors } = form.formState
  const numero = { valueAsNumber: true } as const

  useEffect(() => form.reset(configuracao), [configuracao, form])

  const salvar = form.handleSubmit((dados) =>
    atualizar.mutate(
      { data: dados },
      {
        onSuccess: (salva) => {
          queryClient.setQueryData(getConfiguracaoObterQueryKey(), salva)
          toast.sucesso('Regras da agenda salvas.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    ),
  )

  return (
    <Cartao>
      <TituloSecao>Regras da agenda</TituloSecao>
      <form onSubmit={salvar} noValidate className="grid gap-4 sm:grid-cols-2">
        <Campo rotulo="Antecedência mínima para cancelar (min)" erro={errors.antecedenciaMinimaCancelamentoMinutos?.message}
          ajuda="Cancelamento e remarcação não têm penalidade; só exigem esta antecedência.">
          {(p) => <Input {...p} type="number" min={0} className="numeros-tabulares" {...form.register('antecedenciaMinimaCancelamentoMinutos', numero)} />}
        </Campo>
        <Campo rotulo="Antecedência mínima para agendar (min)" erro={errors.antecedenciaMinimaAgendamentoMinutos?.message}>
          {(p) => <Input {...p} type="number" min={0} className="numeros-tabulares" {...form.register('antecedenciaMinimaAgendamentoMinutos', numero)} />}
        </Campo>
        <Campo rotulo="Agenda aberta para os próximos (dias)" erro={errors.horizonteAgendamentoDias?.message}>
          {(p) => <Input {...p} type="number" min={1} max={365} className="numeros-tabulares" {...form.register('horizonteAgendamentoDias', numero)} />}
        </Campo>
        <Campo rotulo="Reserva durante o pagamento (min)" erro={errors.minutosHoldPagamento?.message} ajuda="Entre 5 e 60 minutos.">
          {(p) => <Input {...p} type="number" min={5} max={60} className="numeros-tabulares" {...form.register('minutosHoldPagamento', numero)} />}
        </Campo>
        <Campo rotulo="Prazo para aceitar vaga da lista de espera (min)" erro={errors.prazoAceiteListaEsperaMinutos?.message}>
          {(p) => <Input {...p} type="number" min={5} className="numeros-tabulares" {...form.register('prazoAceiteListaEsperaMinutos', numero)} />}
        </Campo>
        <Campo rotulo="Lembretes (min antes, separados por vírgula)" erro={errors.lembretesMinutos?.message} ajuda="Ex.: 1440, 120 (24 h e 2 h antes).">
          {(p) => (
            <Input
              {...p}
              className="numeros-tabulares"
              defaultValue={configuracao.lembretesMinutos.join(', ')}
              onChange={(e) =>
                form.setValue(
                  'lembretesMinutos',
                  e.target.value.split(',').map((v) => Number(v.trim())).filter((n) => Number.isFinite(n) && n > 0),
                  { shouldValidate: true },
                )
              }
            />
          )}
        </Campo>
        <Interruptor rotulo="SMS como alternativa" descricao="Usa SMS quando o WhatsApp falhar." {...form.register('smsFallbackAtivo')} />
        <Interruptor
          rotulo="Justificativa no prontuário sem vínculo"
          descricao="Exige justificativa ao abrir prontuário de paciente sem atendimento recente."
          {...form.register('exigeJustificativaProntuarioSemVinculo')}
        />
        <div className="flex justify-end sm:col-span-2">
          <Button type="submit" disabled={atualizar.isPending}>
            {atualizar.isPending ? 'Salvando…' : 'Salvar regras'}
          </Button>
        </div>
      </form>
    </Cartao>
  )
}
