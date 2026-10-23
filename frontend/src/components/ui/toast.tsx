import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { CheckCircle2, CircleAlert, X } from 'lucide-react'

type Tipo = 'sucesso' | 'erro'
interface Toast {
  id: number
  tipo: Tipo
  mensagem: string
}

interface ContextoToast {
  sucesso: (mensagem: string) => void
  erro: (mensagem: string) => void
}

const Contexto = createContext<ContextoToast | null>(null)
const DURACAO_MS = 5000

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const proximoId = useRef(1)

  const remover = useCallback((id: number) => setToasts((lista) => lista.filter((t) => t.id !== id)), [])
  const mostrar = useCallback(
    (tipo: Tipo, mensagem: string) => {
      const id = proximoId.current++
      setToasts((lista) => [...lista.slice(-2), { id, tipo, mensagem }])
      window.setTimeout(() => remover(id), DURACAO_MS)
    },
    [remover],
  )
  const valor = useMemo(
    () => ({ sucesso: (m: string) => mostrar('sucesso', m), erro: (m: string) => mostrar('erro', m) }),
    [mostrar],
  )

  return (
    <Contexto.Provider value={valor}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-4 bottom-4 z-50 flex flex-col items-end gap-2 sm:left-auto">
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.tipo === 'erro' ? 'alert' : 'status'}
            className="pointer-events-auto flex w-full max-w-sm animate-entrada items-start gap-3 rounded-cartao border border-borda bg-superficie p-4 text-sm shadow-toast"
          >
            {t.tipo === 'sucesso' ? (
              <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            ) : (
              <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            )}
            <p className="flex-1">{t.mensagem}</p>
            <button type="button" aria-label="Fechar aviso" onClick={() => remover(t.id)} className="rounded p-0.5 text-texto-3 hover:text-texto">
              <X className="size-4" aria-hidden="true" />
            </button>
          </div>
        ))}
      </div>
    </Contexto.Provider>
  )
}

export function useToast(): ContextoToast {
  const contexto = useContext(Contexto)
  if (!contexto) throw new Error('useToast fora do ToastProvider')
  return contexto
}
