import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/campos'
import { Carregando, Cartao, TituloSecao } from '@/components/ui/estrutura'
import { useToast } from '@/components/ui/toast'
import {
  getAgendaProfissionalExpedienteQueryKey,
  useAgendaProfissionalDefinirExpediente,
  useAgendaProfissionalExpediente,
} from '@/api/gerado/agenda-profissional/agenda-profissional'
import type { FaixaDto, FaixaDtoDia } from '@/api/gerado/modelos'
import { mensagemErro } from '@/lib/formatos'

export const DIAS: { dia: FaixaDtoDia; rotulo: string }[] = [
  { dia: 'MONDAY', rotulo: 'Segunda' },
  { dia: 'TUESDAY', rotulo: 'Terça' },
  { dia: 'WEDNESDAY', rotulo: 'Quarta' },
  { dia: 'THURSDAY', rotulo: 'Quinta' },
  { dia: 'FRIDAY', rotulo: 'Sexta' },
  { dia: 'SATURDAY', rotulo: 'Sábado' },
  { dia: 'SUNDAY', rotulo: 'Domingo' },
]

/** "08:00:00" → "08:00" (o Jackson omite os segundos zerados, mas nem sempre). */
const hora = (valor: string) => valor.slice(0, 5)

/** Faixas que se cruzam no mesmo dia; o servidor também recusa, aqui só antecipamos o aviso. */
export function problemaExpediente(faixas: FaixaDto[]): string | null {
  for (const { dia, rotulo } of DIAS) {
    const doDia = faixas.filter((f) => f.dia === dia).sort((a, b) => a.inicio.localeCompare(b.inicio))
    for (const [i, f] of doDia.entries()) {
      if (f.fim <= f.inicio) return `${rotulo}: o fim precisa ser depois do início.`
      const proxima = doDia[i + 1]
      if (proxima && proxima.inicio < f.fim) return `${rotulo}: há faixas sobrepostas.`
    }
  }
  return null
}

export function Expediente({ profissionalId, editavel }: { profissionalId: string; editavel: boolean }) {
  const salvo = useAgendaProfissionalExpediente(profissionalId)
  return (
    <Cartao>
      <TituloSecao>Expediente semanal</TituloSecao>
      {salvo.isPending ? (
        <Carregando />
      ) : (
        // A chave recria o editor quando o servidor devolve um expediente novo (após salvar ou recarregar).
        <EditorExpediente key={salvo.dataUpdatedAt} profissionalId={profissionalId} editavel={editavel} inicial={salvo.data ?? []} />
      )}
    </Cartao>
  )
}

function EditorExpediente({ profissionalId, editavel, inicial }: { profissionalId: string; editavel: boolean; inicial: FaixaDto[] }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const definir = useAgendaProfissionalDefinirExpediente()
  const [faixas, setFaixas] = useState(() => inicial.map((f) => ({ ...f, inicio: hora(f.inicio), fim: hora(f.fim) })))

  const problema = problemaExpediente(faixas)
  const alterar = (indice: number, mudanca: Partial<FaixaDto>) =>
    setFaixas((lista) => lista.map((f, i) => (i === indice ? { ...f, ...mudanca } : f)))

  const salvar = () =>
    definir.mutate(
      { id: profissionalId, data: faixas },
      {
        onSuccess: (resposta) => {
          queryClient.setQueryData(getAgendaProfissionalExpedienteQueryKey(profissionalId), resposta)
          toast.sucesso('Expediente salvo.')
        },
        onError: (e) => toast.erro(mensagemErro(e)),
      },
    )

  return (
    <>
      <ul className="flex flex-col divide-y divide-borda">
        {DIAS.map(({ dia, rotulo }) => {
          const doDia = faixas.map((f, i) => ({ f, i })).filter(({ f }) => f.dia === dia)
          return (
            <li key={dia} className="flex flex-wrap items-start gap-3 py-3">
              <span className="w-20 pt-2.5 text-sm font-medium">{rotulo}</span>
              <div className="flex flex-1 flex-col gap-2">
                {!doDia.length && <span className="pt-2.5 text-sm text-texto-3">Sem atendimento</span>}
                {doDia.map(({ f, i }) =>
                  editavel ? (
                    <div key={i} className="flex items-center gap-2">
                      <Input type="time" aria-label={`${rotulo}: início`} value={f.inicio} className="w-32 numeros-tabulares"
                        onChange={(e) => alterar(i, { inicio: e.target.value })} />
                      <span aria-hidden="true">–</span>
                      <Input type="time" aria-label={`${rotulo}: fim`} value={f.fim} className="w-32 numeros-tabulares"
                        onChange={(e) => alterar(i, { fim: e.target.value })} />
                      <Button variante="fantasma" tamanho="icone" aria-label={`Remover faixa de ${rotulo}`}
                        onClick={() => setFaixas((lista) => lista.filter((_, j) => j !== i))}>
                        <Trash2 aria-hidden="true" />
                      </Button>
                    </div>
                  ) : (
                    <span key={i} className="pt-2.5 text-sm numeros-tabulares">{f.inicio} – {f.fim}</span>
                  ),
                )}
              </div>
              {editavel && (
                <Button variante="fantasma" aria-label={`Adicionar faixa em ${rotulo}`}
                  onClick={() => setFaixas((lista) => [...lista, { dia, inicio: '08:00', fim: '12:00' }])}>
                  <Plus aria-hidden="true" /> Faixa
                </Button>
              )}
            </li>
          )
        })}
      </ul>
      {editavel && (
        <div className="mt-4 flex flex-wrap items-center justify-end gap-3">
          {problema && <p role="alert" className="text-sm font-medium">{problema}</p>}
          <Button onClick={salvar} disabled={Boolean(problema) || definir.isPending}>
            {definir.isPending ? 'Salvando…' : 'Salvar expediente'}
          </Button>
        </div>
      )}
    </>
  )
}
