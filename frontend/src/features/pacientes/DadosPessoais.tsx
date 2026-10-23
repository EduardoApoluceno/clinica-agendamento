import type { FieldErrors, UseFormRegister } from 'react-hook-form'
import { z } from 'zod'
import { Campo, Input, Select } from '@/components/ui/campos'
import type { PacienteResposta } from '@/api/gerado/modelos'
import { mascararCpf } from '@/lib/formatos'
import { CANAIS, cpfValido, textoObrigatorio } from '@/lib/formulario'

/** Mesmas regras do DadosPessoaisDto do backend, com mensagens para o navegador. */
export const SchemaDadosPessoais = z.object({
  nome: textoObrigatorio(160, 'Informe o nome completo.'),
  nomeSocial: z.string().trim().max(160).optional(),
  dataNascimento: z.iso
    .date('Informe a data de nascimento.')
    .refine((d) => d < new Date().toISOString().slice(0, 10), 'A data precisa estar no passado.'),
  cpf: z.string().refine(cpfValido, 'CPF inválido.'),
  sexo: z.string().trim().max(20).optional(),
  canalPreferido: z.enum(['WHATSAPP', 'EMAIL', 'SMS']),
})
export type DadosPessoais = z.infer<typeof SchemaDadosPessoais>

export const DADOS_VAZIOS: DadosPessoais = {
  nome: '',
  nomeSocial: '',
  dataNascimento: '',
  cpf: '',
  sexo: '',
  canalPreferido: 'WHATSAPP',
}

export function dadosDoPaciente(p: PacienteResposta): DadosPessoais {
  return {
    nome: p.nome ?? '',
    nomeSocial: p.nomeSocial ?? '',
    dataNascimento: p.dataNascimento ?? '',
    cpf: p.cpf ?? '',
    sexo: p.sexo ?? '',
    canalPreferido: p.canalPreferido ?? 'WHATSAPP',
  }
}

/** Campos de dados pessoais para um formulário cujo objeto fica em `dados`. */
export function CamposDadosPessoais({ register, errors, cpfBloqueado = false }: {
  register: UseFormRegister<{ dados: DadosPessoais }>
  errors: FieldErrors<DadosPessoais> | undefined
  cpfBloqueado?: boolean
}) {
  return (
    <>
      <Campo rotulo="Nome completo" obrigatorio erro={errors?.nome?.message} className="sm:col-span-4">
        {(p) => <Input {...p} autoComplete="name" {...register('dados.nome')} />}
      </Campo>
      <Campo rotulo="Nome social" erro={errors?.nomeSocial?.message} ajuda="Como prefere ser chamado(a)." className="sm:col-span-2">
        {(p) => <Input {...p} {...register('dados.nomeSocial')} />}
      </Campo>
      <Campo rotulo="CPF" obrigatorio erro={errors?.cpf?.message} className="sm:col-span-2"
        {...(cpfBloqueado ? { ajuda: 'Para corrigir o CPF, fale com a recepção.' } : {})}>
        {(p) => (
          <Input {...p} inputMode="numeric" autoComplete="off" readOnly={cpfBloqueado} className="numeros-tabulares"
            {...register('dados.cpf', { onChange: (e) => { e.target.value = mascararCpf(e.target.value) } })} />
        )}
      </Campo>
      <Campo rotulo="Data de nascimento" obrigatorio erro={errors?.dataNascimento?.message} className="sm:col-span-2">
        {(p) => <Input {...p} type="date" autoComplete="bday" className="numeros-tabulares" {...register('dados.dataNascimento')} />}
      </Campo>
      <Campo rotulo="Sexo" erro={errors?.sexo?.message} className="sm:col-span-2">
        {(p) => <Input {...p} maxLength={20} {...register('dados.sexo')} />}
      </Campo>
      <Campo rotulo="Prefere receber avisos por" className="sm:col-span-2">
        {(p) => (
          <Select {...p} {...register('dados.canalPreferido')}>
            {Object.entries(CANAIS).map(([valor, rotulo]) => <option key={valor} value={valor}>{rotulo}</option>)}
          </Select>
        )}
      </Campo>
    </>
  )
}
