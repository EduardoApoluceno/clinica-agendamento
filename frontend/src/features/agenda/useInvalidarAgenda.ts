import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getAgendaDiaDiaQueryKey } from '@/api/gerado/agenda-dia/agenda-dia'
import { getAgendamentosMeusQueryKey } from '@/api/gerado/agendamentos/agendamentos'
import { getDisponibilidadeHorariosQueryKey } from '@/api/gerado/disponibilidade/disponibilidade'

/** Depois de qualquer alteração na agenda: grade do dia, "meus agendamentos" e horários livres. */
export function useInvalidarAgenda() {
  const queryClient = useQueryClient()
  return useCallback(() => {
    for (const queryKey of [getAgendaDiaDiaQueryKey(), getAgendamentosMeusQueryKey(), getDisponibilidadeHorariosQueryKey()]) {
      void queryClient.invalidateQueries({ queryKey })
    }
  }, [queryClient])
}
