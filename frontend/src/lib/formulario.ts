import { z } from 'zod'

/**
 * Tipo de um corpo depois de {@link corpoLimpo}: chaves que aceitam `undefined` viram opcionais de verdade,
 * como os modelos gerados pelo Orval esperam com `exactOptionalPropertyTypes`.
 */
export type Limpo<T> = T extends readonly unknown[]
  ? T
  : T extends object
    ? { [K in keyof T as undefined extends T[K] ? never : K]: Limpo<T[K]> } & {
        [K in keyof T as undefined extends T[K] ? K : never]?: Limpo<Exclude<T[K], undefined>>
      }
    : T

/** Remove `undefined` e textos vazios (campo opcional deixado em branco) antes de enviar à API. */
export function corpoLimpo<T extends object>(corpo: T): Limpo<T> {
  const resultado: Record<string, unknown> = {}
  for (const [chave, valor] of Object.entries(corpo)) {
    if (valor === undefined || (typeof valor === 'string' && valor.trim() === '')) continue
    resultado[chave] = valor !== null && typeof valor === 'object' && !Array.isArray(valor) ? corpoLimpo(valor) : valor
  }
  return resultado as Limpo<T>
}

/**
 * Texto obrigatório. O OpenAPI expõe `@NotBlank` como `minLength: 0`, então o schema gerado aceita vazio;
 * este reforço dá a mensagem certa no navegador (a regra real continua no servidor).
 */
export function textoObrigatorio(max: number, mensagem = 'Campo obrigatório.') {
  return z.string().trim().min(1, mensagem).max(max)
}

/** Opções do `register` para número opcional: campo vazio vira `undefined` (e não NaN, como em `valueAsNumber`). */
export const NUMERO_OPCIONAL = { setValueAs: (v: unknown) => (v === '' || v == null ? undefined : Number(v)) } as const

export const CANAIS = { WHATSAPP: 'WhatsApp', EMAIL: 'E-mail', SMS: 'SMS' } as const

export const FINALIDADES_CONSENTIMENTO = {
  TRATAMENTO_SAUDE: { rotulo: 'Tratamento de saúde', descricao: 'Uso dos seus dados para agendar e registrar atendimentos.' },
  LEMBRETES_WHATSAPP: { rotulo: 'Lembretes por WhatsApp', descricao: 'Confirmações e lembretes de consulta.' },
  LEMBRETES_EMAIL: { rotulo: 'Lembretes por e-mail', descricao: 'Confirmações e lembretes de consulta.' },
  LEMBRETES_SMS: { rotulo: 'Lembretes por SMS', descricao: 'Usado quando o WhatsApp falhar.' },
  PESQUISA_SATISFACAO: { rotulo: 'Pesquisa de satisfação', descricao: 'Uma pergunta curta após o atendimento.' },
  COMUNICACOES_CLINICA: { rotulo: 'Comunicações da clínica', descricao: 'Avisos gerais, como mudança de horário.' },
} as const

/** Versão do texto dos termos aceita pelo paciente; muda quando o texto mudar. */
export const VERSAO_TERMO = '2026-10'

/** Dígitos verificadores do CPF (a validação definitiva é do servidor). */
export function cpfValido(valor: string): boolean {
  const d = valor.replace(/\D/g, '')
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false
  const digito = (tamanho: number) => {
    const soma = [...d.slice(0, tamanho)].reduce((total, n, i) => total + Number(n) * (tamanho + 1 - i), 0)
    return ((soma * 10) % 11) % 10
  }
  return digito(9) === Number(d[9]) && digito(10) === Number(d[10])
}

/** Celular brasileiro com DDD: 11 dígitos com 9 após o DDD (aceita +55 na frente). */
export function celularValido(valor: string): boolean {
  return /^[1-9]{2}9\d{8}$/.test(valor.replace(/\D/g, '').replace(/^55(?=\d{11}$)/, ''))
}
