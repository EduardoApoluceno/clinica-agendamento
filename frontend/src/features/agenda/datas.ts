import { formatInTimeZone } from 'date-fns-tz'
import { FUSO_CLINICA } from '@/lib/utils'

/** Datas da agenda como 'yyyy-MM-dd' no fuso da clínica (independe do fuso do navegador). */
export function hojeNaClinica(agora: Date = new Date()): string {
  return formatInTimeZone(agora, FUSO_CLINICA, 'yyyy-MM-dd')
}

export function somarDias(dataIso: string, dias: number): string {
  const [ano, mes, dia] = dataIso.split('-').map(Number) as [number, number, number]
  return new Date(Date.UTC(ano, mes - 1, dia + dias)).toISOString().slice(0, 10)
}

function comoDataUtc(dataIso: string): Date {
  const [ano, mes, dia] = dataIso.split('-').map(Number) as [number, number, number]
  return new Date(Date.UTC(ano, mes - 1, dia, 12))
}

const EXTENSO = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })
const SEMANA_CURTA = new Intl.DateTimeFormat('pt-BR', { weekday: 'short', timeZone: 'UTC' })
const DIA_MES = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', timeZone: 'UTC' })

/** "sexta-feira, 23 de outubro" */
export function diaPorExtenso(dataIso: string): string {
  return EXTENSO.format(comoDataUtc(dataIso))
}

/** { semana: "sex.", dia: "23/10" } para os chips de dia. */
export function diaCurto(dataIso: string): { semana: string; dia: string } {
  const data = comoDataUtc(dataIso)
  return { semana: SEMANA_CURTA.format(data), dia: DIA_MES.format(data) }
}

export function hora(instanteIso: string): string {
  return formatInTimeZone(instanteIso, FUSO_CLINICA, 'HH:mm')
}

export function dataDoInstante(instanteIso: string): string {
  return formatInTimeZone(instanteIso, FUSO_CLINICA, 'yyyy-MM-dd')
}

/** Minutos desde a meia-noite (horário da clínica). */
export function minutosNoDia(instanteIso: string): number {
  const [h, m] = formatInTimeZone(instanteIso, FUSO_CLINICA, 'HH:mm').split(':').map(Number) as [number, number]
  return h * 60 + m
}

export function faixaHoraria(inicio: string, fim: string): string {
  return `${hora(inicio)}–${hora(fim)}`
}

/** Link "Adicionar ao Google Agenda" (modelo de evento; nada é enviado ao Google pelo sistema). */
export function linkGoogleAgenda(titulo: string, inicio: string, fim: string, detalhes: string): string {
  const formato = (iso: string) => iso.replace(/[-:]/g, '').replace(/\.\d{3}/, '')
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: titulo,
    dates: `${formato(new Date(inicio).toISOString())}/${formato(new Date(fim).toISOString())}`,
    details: detalhes,
    ctz: FUSO_CLINICA,
  })
  return `https://calendar.google.com/calendar/render?${params.toString()}`
}
