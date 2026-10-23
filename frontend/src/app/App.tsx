import { lazy, Suspense, useEffect, type ReactNode } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { AppShell } from '@/components/layout/AppShell'
import { BannerDemo } from '@/components/layout/BannerDemo'
import { temPermissao, useUsuarioAtual, type UsuarioAtual } from '@/features/sessao/useUsuarioAtual'
import { api, ErroApi, irParaLogin } from '@/lib/api'
import { EmConstrucao } from '@/pages/EmConstrucao'
import { Inicio } from '@/pages/Inicio'

// Cada tela vira um pedaço separado do bundle: o paciente não baixa as telas da equipe.
const AuditoriaPage = lazy(() => import('@/pages/AuditoriaPage').then((m) => ({ default: m.AuditoriaPage })))
const CaixaSaidaPage = lazy(() => import('@/pages/CaixaSaidaPage').then((m) => ({ default: m.CaixaSaidaPage })))
const ConfiguracoesPage = lazy(() => import('@/pages/ConfiguracoesPage').then((m) => ({ default: m.ConfiguracoesPage })))
const MeuCadastroPage = lazy(() => import('@/pages/MeuCadastroPage').then((m) => ({ default: m.MeuCadastroPage })))
const PacientePage = lazy(() => import('@/pages/PacientePage').then((m) => ({ default: m.PacientePage })))
const PacientesPage = lazy(() => import('@/pages/PacientesPage').then((m) => ({ default: m.PacientesPage })))
const ProfissionaisPage = lazy(() => import('@/pages/ProfissionaisPage').then((m) => ({ default: m.ProfissionaisPage })))
const ProfissionalPage = lazy(() => import('@/pages/ProfissionalPage').then((m) => ({ default: m.ProfissionalPage })))
const MinhaAgendaPage = lazy(() => import('@/pages/ProfissionalPage').then((m) => ({ default: m.MinhaAgendaPage })))
const ServicosPage = lazy(() => import('@/pages/ServicosPage').then((m) => ({ default: m.ServicosPage })))

function useAmbiente() {
  return useQuery({
    queryKey: ['ambiente'],
    queryFn: () => api<{ demo: boolean }>('/api/v1/publico/ambiente'),
    staleTime: Infinity,
  })
}

function Carregando() {
  return (
    <div role="status" aria-live="polite" className="grid min-h-dvh place-items-center text-sm text-texto-3">
      Carregando…
    </div>
  )
}

/** Esconde a tela de quem não tem a permissão; a autorização de verdade é no servidor. */
function Exige({ usuario, permissoes, children }: { usuario: UsuarioAtual; permissoes: string[]; children: ReactNode }) {
  if (permissoes.some((p) => temPermissao(usuario, p))) return children
  return <EmConstrucao titulo="Acesso não permitido" fase="—" descricao="Seu perfil não tem acesso a esta tela." />
}

function AreaAutenticada() {
  const { data: usuario, error, isPending } = useUsuarioAtual()
  const naoAutenticado = error instanceof ErroApi && error.status === 401

  useEffect(() => {
    if (naoAutenticado) irParaLogin()
  }, [naoAutenticado])

  if (isPending || naoAutenticado) return <Carregando />
  if (!usuario) {
    return (
      <div role="alert" className="grid min-h-dvh place-items-center text-sm text-texto-2">
        Não foi possível carregar sua sessão. Tente novamente em instantes.
      </div>
    )
  }

  return (
    <AppShell usuario={usuario}>
      <Suspense fallback={<p role="status" className="py-8 text-center text-sm text-texto-3">Carregando…</p>}>
        <Routes>
          <Route index element={<Inicio usuario={usuario} />} />
          <Route
            path="agenda"
            element={<EmConstrucao titulo="Agenda do dia" fase="Fase 2" descricao="Grade por profissional, resumo do dia e painel de detalhes." />}
          />
          <Route
            path="agendar"
            element={<EmConstrucao titulo="Agendar consulta" fase="Fase 2" descricao="Serviço → profissional → data e horário → confirmação." />}
          />
          <Route
            path="indicadores"
            element={<EmConstrucao titulo="Indicadores" fase="Fase 6" descricao="KPIs, ocupação, origem dos agendamentos e lista de espera." />}
          />
          <Route path="minha-agenda" element={<Exige usuario={usuario} permissoes={['agenda-propria:gerir']}><MinhaAgendaPage /></Exige>} />
          <Route path="pacientes" element={<Exige usuario={usuario} permissoes={['pacientes:ler']}><PacientesPage /></Exige>} />
          <Route path="pacientes/:id" element={<Exige usuario={usuario} permissoes={['pacientes:ler']}><PacientePage /></Exige>} />
          <Route path="profissionais" element={<Exige usuario={usuario} permissoes={['profissionais:ler']}><ProfissionaisPage /></Exige>} />
          <Route path="profissionais/:id" element={<Exige usuario={usuario} permissoes={['profissionais:ler']}><ProfissionalPage /></Exige>} />
          <Route path="servicos" element={<Exige usuario={usuario} permissoes={['profissionais:ler']}><ServicosPage /></Exige>} />
          <Route path="meu-cadastro" element={<Exige usuario={usuario} permissoes={['cadastro-proprio:gerir']}><MeuCadastroPage /></Exige>} />
          <Route path="auditoria" element={<Exige usuario={usuario} permissoes={['auditoria:ler']}><AuditoriaPage /></Exige>} />
          <Route path="configuracoes" element={<Exige usuario={usuario} permissoes={['clinica:administrar']}><ConfiguracoesPage /></Exige>} />
          <Route path="caixa-saida" element={<Exige usuario={usuario} permissoes={['caixa-saida:ler']}><CaixaSaidaPage /></Exige>} />
          <Route path="*" element={<EmConstrucao titulo="Página não encontrada" fase="—" descricao="Verifique o endereço." />} />
        </Routes>
      </Suspense>
    </AppShell>
  )
}

export function App() {
  const { data: ambiente } = useAmbiente()
  return (
    <BrowserRouter>
      {ambiente?.demo && <BannerDemo />}
      <AreaAutenticada />
    </BrowserRouter>
  )
}
