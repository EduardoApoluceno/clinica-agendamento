import { useEffect } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { AppShell } from '@/components/layout/AppShell'
import { BannerDemo } from '@/components/layout/BannerDemo'
import { useUsuarioAtual } from '@/features/sessao/useUsuarioAtual'
import { api, ErroApi, irParaLogin } from '@/lib/api'
import { EmConstrucao } from '@/pages/EmConstrucao'
import { Inicio } from '@/pages/Inicio'

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
        <Route
          path="caixa-saida"
          element={<EmConstrucao titulo="Caixa de saída" fase="Fase 3" descricao="Mensagens simuladas de WhatsApp/SMS com botões funcionais." />}
        />
        <Route path="*" element={<EmConstrucao titulo="Página não encontrada" fase="—" descricao="Verifique o endereço." />} />
      </Routes>
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
