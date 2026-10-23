import { useState, type ReactNode } from 'react'
import { NavLink } from 'react-router'
import {
  BarChart3, CalendarCheck, CalendarClock, CalendarDays, CalendarPlus, ClipboardList, Inbox, LogOut, Menu, Settings, ShieldCheck,
  Stethoscope, UserRound, Users, X, type LucideIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { sair } from '@/lib/api'
import { cn } from '@/lib/utils'
import type { Papel, UsuarioAtual } from '@/features/sessao/useUsuarioAtual'

interface ItemMenu {
  rota: string
  rotulo: string
  icone: LucideIcon
  /** Basta uma das permissões (vindas de /api/v1/me). */
  permissoes?: string[]
  /** Telas de fases futuras, ainda sem permissão própria no backend. */
  papeis?: Papel[]
  somenteDemo?: boolean
}

const GESTAO_EQUIPE = ['profissionais:administrar', 'ausencias:gerir']

export const ITENS_MENU: ItemMenu[] = [
  { rota: '/agenda', rotulo: 'Agenda do dia', icone: CalendarDays, permissoes: ['agenda:ler', 'agenda-propria:gerir'] },
  { rota: '/agendar', rotulo: 'Agendar consulta', icone: CalendarPlus, permissoes: ['agendamento-proprio:gerir'] },
  { rota: '/meus-agendamentos', rotulo: 'Meus agendamentos', icone: CalendarCheck, permissoes: ['agendamento-proprio:gerir'] },
  { rota: '/minha-agenda', rotulo: 'Minha agenda', icone: CalendarClock, permissoes: ['agenda-propria:gerir'] },
  { rota: '/pacientes', rotulo: 'Pacientes', icone: Users, permissoes: ['pacientes:ler'] },
  { rota: '/profissionais', rotulo: 'Profissionais', icone: Stethoscope, permissoes: GESTAO_EQUIPE },
  { rota: '/servicos', rotulo: 'Serviços', icone: ClipboardList, permissoes: GESTAO_EQUIPE },
  { rota: '/meu-cadastro', rotulo: 'Meu cadastro', icone: UserRound, permissoes: ['cadastro-proprio:gerir'] },
  { rota: '/indicadores', rotulo: 'Indicadores', icone: BarChart3, papeis: ['ADMIN'] },
  { rota: '/auditoria', rotulo: 'Auditoria', icone: ShieldCheck, permissoes: ['auditoria:ler'] },
  { rota: '/configuracoes', rotulo: 'Configurações', icone: Settings, permissoes: ['clinica:administrar'] },
  { rota: '/caixa-saida', rotulo: 'Caixa de saída', icone: Inbox, permissoes: ['caixa-saida:ler'], somenteDemo: true },
]

export function itemVisivel(item: ItemMenu, usuario: UsuarioAtual) {
  const permitido =
    (item.permissoes?.some((p) => usuario.permissoes?.includes(p)) ?? false) ||
    (item.papeis?.some((p) => usuario.papeis.includes(p)) ?? false)
  return permitido && (!item.somenteDemo || usuario.ambienteDemo)
}

export function itensVisiveis(usuario: UsuarioAtual) {
  return ITENS_MENU.filter((item) => itemVisivel(item, usuario))
}

export function AppShell({ usuario, children }: { usuario: UsuarioAtual; children: ReactNode }) {
  const [menuAberto, setMenuAberto] = useState(false)
  const itens = itensVisiveis(usuario)

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[248px_1fr]">
      {/* Barra superior (celular) */}
      <header className="flex items-center justify-between border-b border-borda bg-superficie px-4 py-2 md:hidden">
        <span className="font-semibold">Clínica</span>
        <Button
          variante="fantasma"
          tamanho="icone"
          aria-label={menuAberto ? 'Fechar menu' : 'Abrir menu'}
          aria-expanded={menuAberto}
          aria-controls="menu-principal"
          onClick={() => setMenuAberto((v) => !v)}
        >
          {menuAberto ? <X /> : <Menu />}
        </Button>
      </header>

      {/* Menu lateral (desktop) / gaveta (celular) */}
      <nav
        id="menu-principal"
        aria-label="Menu principal"
        className={cn(
          'border-borda bg-superficie md:sticky md:top-0 md:flex md:h-dvh md:flex-col md:border-r',
          menuAberto ? 'flex flex-col border-b' : 'hidden',
        )}
      >
        <div className="hidden px-5 pt-6 pb-4 text-lg font-semibold md:block">Clínica</div>
        <ul className="flex flex-col gap-1 px-3 py-2">
          {itens.map(({ rota, rotulo, icone: Icone }) => (
            <li key={rota}>
              <NavLink
                to={rota}
                onClick={() => setMenuAberto(false)}
                className={({ isActive }) =>
                  cn(
                    'flex min-h-toque items-center gap-3 rounded-botao px-3 text-sm font-medium transition-colors duration-[var(--duracao-rapida)]',
                    isActive ? 'bg-superficie-2 text-texto shadow-sutil' : 'text-texto-2 hover:bg-superficie-2 hover:text-texto',
                  )
                }
              >
                <Icone className="size-4" aria-hidden="true" />
                {rotulo}
              </NavLink>
            </li>
          ))}
        </ul>
        <div className="mt-auto border-t border-borda px-3 py-3">
          <p className="truncate px-3 text-sm font-medium">{usuario.nome}</p>
          <p className="px-3 pb-2 text-xs text-texto-3">{usuario.papeis.join(' · ')}</p>
          <Button variante="fantasma" className="w-full justify-start" onClick={sair}>
            <LogOut aria-hidden="true" /> Sair
          </Button>
        </div>
      </nav>

      <main className="min-w-0 animate-entrada px-4 py-6 md:px-8">{children}</main>
    </div>
  )
}
