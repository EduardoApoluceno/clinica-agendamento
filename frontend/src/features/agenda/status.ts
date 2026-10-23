/** Rótulos e estilo de cada status do agendamento — diferenciados por forma, nunca só por cor. */
export type StatusAgendamento =
  | 'RESERVADO'
  | 'AGUARDANDO_CONFIRMACAO'
  | 'CONFIRMADO'
  | 'PRESENTE'
  | 'REALIZADO'
  | 'FALTA'
  | 'CANCELADO'
  | 'EXPIRADO'

type Forma = 'preenchido' | 'contorno' | 'tracejado'

export const STATUS: Record<StatusAgendamento, { rotulo: string; forma: Forma }> = {
  RESERVADO: { rotulo: 'Aguardando pagamento', forma: 'tracejado' },
  AGUARDANDO_CONFIRMACAO: { rotulo: 'Aguardando confirmação', forma: 'tracejado' },
  CONFIRMADO: { rotulo: 'Confirmado', forma: 'preenchido' },
  PRESENTE: { rotulo: 'Paciente presente', forma: 'preenchido' },
  REALIZADO: { rotulo: 'Realizado', forma: 'contorno' },
  FALTA: { rotulo: 'Faltou', forma: 'contorno' },
  CANCELADO: { rotulo: 'Cancelado', forma: 'contorno' },
  EXPIRADO: { rotulo: 'Reserva expirada', forma: 'contorno' },
}

export function rotuloStatus(status: string | undefined): string {
  return status && status in STATUS ? STATUS[status as StatusAgendamento].rotulo : '—'
}

export function formaStatus(status: string | undefined): Forma {
  return status && status in STATUS ? STATUS[status as StatusAgendamento].forma : 'contorno'
}

/** Ainda não aconteceu: pode ser remarcado ou cancelado. */
export function pendente(status: string | undefined): boolean {
  return status === 'RESERVADO' || status === 'AGUARDANDO_CONFIRMACAO' || status === 'CONFIRMADO'
}

/** Pode registrar presença ou falta. */
export function aguardandoAtendimento(status: string | undefined): boolean {
  return status === 'AGUARDANDO_CONFIRMACAO' || status === 'CONFIRMADO'
}
