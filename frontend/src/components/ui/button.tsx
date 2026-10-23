import type { ComponentProps } from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

// Botão no padrão shadcn/ui, com o tema da clínica (cinza-escuro, cantos de 10px, escala ao clicar).
const variantesBotao = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-botao text-sm font-medium ' +
    'transition-[background-color,border-color,transform] duration-[var(--duracao-rapida)] ease-[var(--ease-suave)] ' +
    'active:scale-[0.97] disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variante: {
        primario: 'bg-acao text-acao-texto hover:bg-acao-hover',
        secundario: 'bg-superficie text-texto border border-borda hover:bg-superficie-2',
        fantasma: 'text-texto-2 hover:bg-superficie-2 hover:text-texto',
      },
      tamanho: {
        padrao: 'min-h-toque px-4',
        paciente: 'min-h-toque-paciente px-5 text-base',
        icone: 'size-toque',
      },
    },
    defaultVariants: { variante: 'primario', tamanho: 'padrao' },
  },
)

type BotaoProps = ComponentProps<'button'> & VariantProps<typeof variantesBotao> & { asChild?: boolean }

export function Button({ className, variante, tamanho, asChild = false, ...props }: BotaoProps) {
  const Componente = asChild ? Slot : 'button'
  return <Componente className={cn(variantesBotao({ variante, tamanho }), className)} {...props} />
}
