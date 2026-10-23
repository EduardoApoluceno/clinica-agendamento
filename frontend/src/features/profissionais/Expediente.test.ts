import { describe, expect, it } from 'vitest'
import { problemaExpediente } from './Expediente'

describe('problemaExpediente', () => {
  it('aceita faixas encostadas no mesmo dia e faixas iguais em dias diferentes', () => {
    expect(problemaExpediente([
      { dia: 'MONDAY', inicio: '08:00', fim: '12:00' },
      { dia: 'MONDAY', inicio: '12:00', fim: '18:00' },
      { dia: 'TUESDAY', inicio: '08:00', fim: '12:00' },
    ])).toBeNull()
  })

  it('aponta sobreposição no dia', () => {
    expect(problemaExpediente([
      { dia: 'WEDNESDAY', inicio: '13:00', fim: '18:00' },
      { dia: 'WEDNESDAY', inicio: '08:00', fim: '14:00' },
    ])).toBe('Quarta: há faixas sobrepostas.')
  })

  it('aponta fim antes do início', () => {
    expect(problemaExpediente([{ dia: 'FRIDAY', inicio: '18:00', fim: '08:00' }])).toBe('Sexta: o fim precisa ser depois do início.')
  })
})
