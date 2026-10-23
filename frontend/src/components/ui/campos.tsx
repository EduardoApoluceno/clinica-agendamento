import { useId, type ComponentProps, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

const baseControle =
  'w-full min-h-toque rounded-botao border border-borda bg-superficie px-3 text-sm text-texto ' +
  'placeholder:text-texto-3 transition-colors duration-[var(--duracao-rapida)] ' +
  'hover:border-borda-forte focus-visible:border-acao disabled:bg-superficie-2 disabled:text-texto-3 ' +
  'aria-[invalid=true]:border-texto aria-[invalid=true]:border-2'

export function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input className={cn(baseControle, className)} {...props} />
}

export function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return <textarea className={cn(baseControle, 'min-h-24 py-2', className)} {...props} />
}

export function Select({ className, ...props }: ComponentProps<'select'>) {
  return <select className={cn(baseControle, 'pr-8', className)} {...props} />
}

interface CampoProps {
  rotulo: string
  erro?: string | undefined
  ajuda?: string
  obrigatorio?: boolean
  className?: string
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode
}

/** Rótulo + controle + ajuda/erro com ligações de acessibilidade (label/for, aria-describedby). */
export function Campo({ rotulo, erro, ajuda, obrigatorio, className, children }: CampoProps) {
  const id = useId()
  const idDescricao = `${id}-descricao`
  const temDescricao = Boolean(erro ?? ajuda)
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-texto">
        {rotulo}
        {obrigatorio && <span aria-hidden="true" className="text-texto-3"> *</span>}
      </label>
      {children({ id, 'aria-invalid': Boolean(erro), ...(temDescricao ? { 'aria-describedby': idDescricao } : {}) })}
      {temDescricao && (
        <p id={idDescricao} className={cn('text-xs', erro ? 'font-medium text-texto' : 'text-texto-3')}>
          {erro ?? ajuda}
        </p>
      )}
    </div>
  )
}

export function Interruptor({
  rotulo,
  descricao,
  className,
  ...props
}: Omit<ComponentProps<'input'>, 'type'> & { rotulo: string; descricao?: string }) {
  const id = useId()
  return (
    <label htmlFor={id} className={cn('flex min-h-toque cursor-pointer items-start gap-3', className)}>
      <input id={id} type="checkbox" role="switch" className="peer sr-only" {...props} />
      <span
        aria-hidden="true"
        className="relative mt-0.5 h-6 w-10 shrink-0 rounded-full border border-borda-forte bg-superficie-2 transition-colors duration-[var(--duracao-media)] peer-checked:border-acao peer-checked:bg-acao peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-acao after:absolute after:top-0.5 after:left-0.5 after:size-4.5 after:rounded-full after:bg-superficie after:shadow-sutil after:transition-transform after:duration-[var(--duracao-media)] after:ease-[var(--ease-suave)] peer-checked:after:translate-x-4"
      />
      <span className="flex flex-col">
        <span className="text-sm font-medium">{rotulo}</span>
        {descricao && <span className="text-xs text-texto-3">{descricao}</span>}
      </span>
    </label>
  )
}
