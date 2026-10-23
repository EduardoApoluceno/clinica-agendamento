import { api } from './api'

/** Adaptador usado pelo código gerado pelo Orval: reaproveita sessão + CSRF de api(). */
export function chamarApi<T>(url: string, init?: RequestInit): Promise<T> {
  return api<T>(url, init)
}
