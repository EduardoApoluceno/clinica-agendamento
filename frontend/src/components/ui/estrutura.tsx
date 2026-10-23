import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function Cartao({ className, ...props }: ComponentProps<'section'>) {
  return <section className={cn('rounded-cartao border border-borda bg-superficie p-5 md:p-6', className)} {...props} />
}

export function TituloPagina({ titulo, descricao, acoes }: { titulo: string; descricao?: string; acoes?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold">{titulo}</h1>
        {descricao && <p className="mt-1 text-sm text-texto-2">{descricao}</p>}
      </div>
      {acoes && <div className="flex flex-wrap gap-2">{acoes}</div>}
    </header>
  )
}

export function TituloSecao({ children, acoes }: { children: ReactNode; acoes?: ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
      <h2 className="text-base font-semibold">{children}</h2>
      {acoes}
    </div>
  )
}

/** Tabela com rolagem horizontal própria (a página nunca rola na horizontal). */
export function Tabela({ className, children, rotulo }: { className?: string; children: ReactNode; rotulo: string }) {
  return (
    <div className={cn('overflow-x-auto rounded-cartao border border-borda bg-superficie', className)}>
      <table aria-label={rotulo} className="w-full min-w-[560px] border-collapse text-left text-sm">
        {children}
      </table>
    </div>
  )
}

export function Th({ className, ...props }: ComponentProps<'th'>) {
  return (
    <th
      scope="col"
      className={cn('border-b border-borda bg-superficie-2 px-4 py-2.5 text-xs font-semibold text-texto-2', className)}
      {...props}
    />
  )
}

export function Td({ className, ...props }: ComponentProps<'td'>) {
  return <td className={cn('border-b border-borda px-4 py-3 align-middle', className)} {...props} />
}

export function Vazio({ titulo, descricao, acao }: { titulo: string; descricao?: string; acao?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-cartao border border-dashed border-borda-forte bg-superficie px-6 py-10 text-center">
      <p className="font-medium">{titulo}</p>
      {descricao && <p className="max-w-md text-sm text-texto-3">{descricao}</p>}
      {acao}
    </div>
  )
}

export function Carregando({ texto = 'Carregando…' }: { texto?: string }) {
  return (
    <p role="status" aria-live="polite" className="py-8 text-center text-sm text-texto-3">
      {texto}
    </p>
  )
}

/** Selo de status: estilos por forma (preenchido, contorno, tracejado), nunca só por cor. */
export function Selo({ variante = 'contorno', children }: { variante?: 'preenchido' | 'contorno' | 'tracejado'; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium',
        variante === 'preenchido' && 'bg-acao text-acao-texto',
        variante === 'contorno' && 'border border-borda-forte text-texto',
        variante === 'tracejado' && 'border border-dashed border-texto-3 text-texto-2',
      )}
    >
      {children}
    </span>
  )
}
