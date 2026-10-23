import { useQuery } from '@tanstack/react-query'
import { api, ErroApi } from '@/lib/api'

export type Papel = 'ADMIN' | 'RECEPCAO' | 'PROFISSIONAL' | 'PACIENTE'

export interface UsuarioAtual {
  id: string
  nome: string
  email?: string | null
  papeis: Papel[]
  permissoes?: string[]
  equipe?: boolean
  ambienteDemo: boolean
}

export const CHAVE_USUARIO_ATUAL = ['me'] as const

export function useUsuarioAtual() {
  return useQuery({
    queryKey: CHAVE_USUARIO_ATUAL,
    queryFn: () => api<UsuarioAtual>('/api/v1/me'),
    retry: (falhas, erro) => !(erro instanceof ErroApi && erro.status === 401) && falhas < 2,
    staleTime: 60_000,
  })
}

/** Permissões finas vindas do backend (ex.: "pacientes:escrever"). A autorização real é no servidor. */
export function temPermissao(usuario: UsuarioAtual | undefined, permissao: string): boolean {
  return usuario?.permissoes?.includes(permissao) ?? false
}

export function usePermissao(permissao: string): boolean {
  const { data } = useUsuarioAtual()
  return temPermissao(data, permissao)
}

export function ehEquipe(usuario: UsuarioAtual) {
  return usuario.papeis.some((p) => p !== 'PACIENTE')
}
