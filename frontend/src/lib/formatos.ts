import { formatInTimeZone, fromZonedTime } from 'date-fns-tz'
import { ErroApi } from './api'
import { FUSO_CLINICA } from './utils'

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatarMoeda(centavos: number): string {
  return MOEDA.format(centavos / 100)
}

/** Instante UTC exibido no fuso da clínica (America/Porto_Velho). */
export function formatarDataHora(instanteIso: string): string {
  return formatInTimeZone(instanteIso, FUSO_CLINICA, "dd/MM/yyyy 'às' HH:mm")
}

export function formatarData(dataIso: string): string {
  const [ano, mes, dia] = dataIso.slice(0, 10).split('-')
  return `${dia}/${mes}/${ano}`
}

/** Valor de <input type="datetime-local"> (horário da clínica) → instante UTC ISO. */
export function localClinicaParaUtc(valorLocal: string): string {
  return fromZonedTime(valorLocal, FUSO_CLINICA).toISOString()
}

/** Instante UTC → valor de <input type="datetime-local"> no horário da clínica. */
export function utcParaLocalClinica(instanteIso: string): string {
  return formatInTimeZone(instanteIso, FUSO_CLINICA, "yyyy-MM-dd'T'HH:mm")
}

export function mascararCpf(valor: string): string {
  const d = valor.replace(/\D/g, '').slice(0, 11)
  return d
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1-$2')
}

export function mascararCelular(valor: string): string {
  const d = valor.replace(/\D/g, '').replace(/^55(?=\d{11})/, '').slice(0, 11)
  if (d.length <= 2) return d
  if (d.length <= 7) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

/** Mensagem amigável a partir de um erro da API (Problem Details) ou desconhecido. */
export function mensagemErro(erro: unknown): string {
  if (erro instanceof ErroApi) {
    if (erro.status === 403) return 'Você não tem permissão para esta ação.'
    if (erro.status === 400 && !erro.problema?.detail) return 'Verifique os campos informados.'
    return erro.problema?.detail ?? `Não foi possível concluir (erro ${erro.status}).`
  }
  return 'Não foi possível concluir. Verifique sua conexão.'
}
