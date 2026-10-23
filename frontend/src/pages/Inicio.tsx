import { Link } from 'react-router'
import { itensVisiveis } from '@/components/layout/AppShell'
import type { UsuarioAtual } from '@/features/sessao/useUsuarioAtual'

export function Inicio({ usuario }: { usuario: UsuarioAtual }) {
  const primeiroNome = usuario.nome.split(' ')[0]
  return (
    <section aria-labelledby="titulo-inicio" className="mx-auto max-w-3xl">
      <h1 id="titulo-inicio" className="text-2xl font-semibold">
        Olá, {primeiroNome}
      </h1>
      <p className="mt-1 text-sm text-texto-2">Você está conectado com segurança.</p>
      <ul className="mt-6 grid gap-3 sm:grid-cols-2">
        {itensVisiveis(usuario).map(({ rota, rotulo, icone: Icone }) => (
          <li key={rota}>
            <Link
              to={rota}
              className="flex min-h-toque items-center gap-3 rounded-cartao border border-borda bg-superficie p-4 transition-[transform,border-color] duration-[var(--duracao-rapida)] hover:border-borda-forte active:scale-[0.97]"
            >
              <Icone className="size-5 text-texto-2" aria-hidden="true" />
              <span className="font-medium">{rotulo}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
