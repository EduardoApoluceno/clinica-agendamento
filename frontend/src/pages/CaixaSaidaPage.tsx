import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Carregando, Selo, TituloPagina, Vazio } from '@/components/ui/estrutura'
import { useCaixaSaidaListar } from '@/api/gerado/caixa-saida/caixa-saida'
import { formatarDataHora } from '@/lib/formatos'

/** Demonstração: mensagens que seriam enviadas por WhatsApp/SMS/e-mail ficam aqui em vez de sair. */
export function CaixaSaidaPage() {
  const mensagens = useCaixaSaidaListar({ query: { refetchInterval: 10_000 } })

  return (
    <div className="mx-auto max-w-3xl">
      <TituloPagina
        titulo="Caixa de saída"
        descricao="Mensagens simuladas do ambiente de demonstração. Nada é enviado de verdade."
        acoes={
          <Button variante="secundario" onClick={() => void mensagens.refetch()} disabled={mensagens.isFetching}>
            <RefreshCw aria-hidden="true" /> Atualizar
          </Button>
        }
      />
      {mensagens.isPending ? (
        <Carregando />
      ) : !mensagens.data?.length ? (
        <Vazio titulo="Nenhuma mensagem" descricao="Códigos de verificação e avisos aparecem aqui." />
      ) : (
        <ul className="flex flex-col gap-3" aria-live="polite">
          {mensagens.data.map((m) => (
            <li key={m.id} className="rounded-cartao border border-borda bg-superficie p-4">
              <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-texto-3">
                <Selo>{m.canal}</Selo>
                <span className="numeros-tabulares">{m.destino}</span>
                <span className="ml-auto numeros-tabulares">{m.criadoEm && formatarDataHora(m.criadoEm)}</span>
              </div>
              <p className="text-sm whitespace-pre-line">{m.conteudo}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
