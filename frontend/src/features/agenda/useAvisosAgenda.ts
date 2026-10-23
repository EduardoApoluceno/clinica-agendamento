import { useEffect, useRef } from 'react'

const URL_AVISOS = '/api/v1/publico/disponibilidade/stream'

/**
 * Assina os avisos "a agenda mudou" (SSE) e chama {@code aoMudar} com o id do profissional. O aviso não
 * traz dados: quem recebe recarrega pela API normal. O EventSource reconecta sozinho quando a conexão
 * expira (o servidor encerra a cada 5 min).
 */
export function useAvisosAgenda(aoMudar: (profissionalId: string) => void, profissionalId?: string) {
  const callback = useRef(aoMudar)
  useEffect(() => {
    callback.current = aoMudar
  })

  useEffect(() => {
    if (typeof EventSource === 'undefined') return
    const url = profissionalId ? `${URL_AVISOS}?profissionalId=${encodeURIComponent(profissionalId)}` : URL_AVISOS
    const fonte = new EventSource(url)
    const ouvir = (evento: MessageEvent<string>) => callback.current(evento.data)
    fonte.addEventListener('agenda-alterada', ouvir)
    return () => {
      fonte.removeEventListener('agenda-alterada', ouvir)
      fonte.close()
    }
  }, [profissionalId])
}
