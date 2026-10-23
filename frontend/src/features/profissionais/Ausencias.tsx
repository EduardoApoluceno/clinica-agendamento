import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { addDays } from 'date-fns'
import { Trash2 } from 'lucide-react'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Campo, Input, Select } from '@/components/ui/campos'
import { Carregando, Cartao, Selo, TituloSecao } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import {
  getAgendaProfissionalAusenciasQueryKey,
  useAgendaProfissionalAusencias,
  useAgendaProfissionalRegistrarAusencia,
  useAgendaProfissionalRemoverAusencia,
} from '@/api/gerado/agenda-profissional/agenda-profissional'
import { formatarDataHora, localClinicaParaUtc, mensagemErro } from '@/lib/formatos'
import { corpoLimpo } from '@/lib/formulario'

const TIPOS = { FERIAS: 'Férias', BLOQUEIO: 'Bloqueio' } as const
const ROTULO_TIPO: Record<string, string> = { ...TIPOS, EXTERNO_GOOGLE: 'Agenda externa' }

/** Janela exibida: de agora até 180 dias (o filtro de período é obrigatório no endpoint). */
const DIAS_JANELA = 180

const SchemaAusencia = z
  .object({
    tipo: z.enum(['FERIAS', 'BLOQUEIO']),
    inicio: z.string().min(1, 'Informe o início.'),
    fim: z.string().min(1, 'Informe o fim.'),
    motivo: z.string().max(200).optional(),
  })
  .refine((a) => !a.inicio || !a.fim || a.fim > a.inicio, { message: 'O fim precisa ser depois do início.', path: ['fim'] })
type DadosAusencia = z.infer<typeof SchemaAusencia>

export function Ausencias({ profissionalId, editavel }: { profissionalId: string; editavel: boolean }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const periodo = useMemo(() => {
    const agora = new Date()
    return { de: agora.toISOString(), ate: addDays(agora, DIAS_JANELA).toISOString() }
  }, [])
  const ausencias = useAgendaProfissionalAusencias(profissionalId, periodo)
  const registrar = useAgendaProfissionalRegistrarAusencia()
  const remover = useAgendaProfissionalRemoverAusencia()
  const form = useForm<DadosAusencia>({
    resolver: zodResolver(SchemaAusencia),
    defaultValues: { tipo: 'FERIAS', inicio: '', fim: '', motivo: '' },
  })
  const { errors } = form.formState

  const atualizarLista = () =>
    queryClient.invalidateQueries({ queryKey: getAgendaProfissionalAusenciasQueryKey(profissionalId, periodo) })

  const salvar = form.handleSubmit((dados) =>
    registrar.mutate(
      {
        id: profissionalId,
        data: corpoLimpo({ ...dados, inicio: localClinicaParaUtc(dados.inicio), fim: localClinicaParaUtc(dados.fim) }),
      },
      {
        onSuccess: () => {
          void atualizarLista()
          form.reset()
          toast.sucesso('Ausência registrada.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    ),
  )

  const excluir = (ausenciaId: string) =>
    remover.mutate(
      { id: profissionalId, ausenciaId },
      {
        onSuccess: () => {
          void atualizarLista()
          toast.sucesso('Ausência removida.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    )

  return (
    <Cartao>
      <TituloSecao>Ausências e bloqueios</TituloSecao>
      {ausencias.isPending ? (
        <Carregando />
      ) : !ausencias.data?.length ? (
        <p className="mb-4 text-sm text-texto-3">Nenhuma ausência nos próximos {DIAS_JANELA} dias.</p>
      ) : (
        <ul className="mb-4 flex flex-col divide-y divide-borda">
          {ausencias.data.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center gap-3 py-3">
              <Selo variante={a.tipo === 'FERIAS' ? 'preenchido' : 'contorno'}>{ROTULO_TIPO[a.tipo ?? ''] ?? a.tipo}</Selo>
              <span className="text-sm numeros-tabulares">
                {a.inicio && formatarDataHora(a.inicio)} até {a.fim && formatarDataHora(a.fim)}
              </span>
              {a.motivo && <span className="text-sm text-texto-3">{a.motivo}</span>}
              {editavel && a.id && a.tipo !== 'EXTERNO_GOOGLE' && (
                <Button variante="fantasma" tamanho="icone" className="ml-auto" aria-label="Remover ausência"
                  disabled={remover.isPending} onClick={() => a.id && excluir(a.id)}>
                  <Trash2 aria-hidden="true" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {editavel && (
        <form onSubmit={salvar} noValidate className="grid gap-4 border-t border-borda pt-4 sm:grid-cols-6">
          <Campo rotulo="Tipo" className="sm:col-span-2">
            {(p) => (
              <Select {...p} {...form.register('tipo')}>
                {Object.entries(TIPOS).map(([valor, rotulo]) => <option key={valor} value={valor}>{rotulo}</option>)}
              </Select>
            )}
          </Campo>
          <Campo rotulo="Início" obrigatorio erro={errors.inicio?.message} className="sm:col-span-2">
            {(p) => <Input {...p} type="datetime-local" className="numeros-tabulares" {...form.register('inicio')} />}
          </Campo>
          <Campo rotulo="Fim" obrigatorio erro={errors.fim?.message} className="sm:col-span-2">
            {(p) => <Input {...p} type="datetime-local" className="numeros-tabulares" {...form.register('fim')} />}
          </Campo>
          <Campo rotulo="Motivo" erro={errors.motivo?.message} ajuda="Visível só para a equipe." className="sm:col-span-4">
            {(p) => <Input {...p} maxLength={200} {...form.register('motivo')} />}
          </Campo>
          <div className="flex items-end justify-end sm:col-span-2">
            <Button type="submit" variante="secundario" disabled={registrar.isPending}>Registrar ausência</Button>
          </div>
        </form>
      )}
    </Cartao>
  )
}
