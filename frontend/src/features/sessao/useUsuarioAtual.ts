import { useQuery } from '@tanstack/react-query'
import { api, ErroApi } from '@/lib/api'

export type Papel = 'ADMIN' | 'RECEPCAO' | 'PROFISSIONAL' | 'PACIENTE'

export interface UsuarioAtual {
  id: string
  nome: string
  email?: string | null
  papeis: Papel[]
  ambienteDemo: boolean
}

export function useUsuarioAtual() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () => api<UsuarioAtual>('/api/v1/me'),
    retry: (falhas, erro) => !(erro instanceof ErroApi && erro.status === 401) && falhas < 2,
    staleTime: 60_000,
  })
}

export function ehEquipe(usuario: UsuarioAtual) {
  return usuario.papeis.some((p) => p !== 'PACIENTE')
}
