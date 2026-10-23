import { describe, expect, it } from 'vitest'
import { celularValido, corpoLimpo, cpfValido, NUMERO_OPCIONAL } from './formulario'

describe('corpoLimpo', () => {
  it('remove undefined e textos vazios, inclusive em objetos aninhados', () => {
    expect(corpoLimpo({ nome: 'Ana', nomeSocial: '', sexo: undefined, endereco: { numero: '10', complemento: '  ' } }))
      .toEqual({ nome: 'Ana', endereco: { numero: '10' } })
  })

  it('mantém zero, false e listas', () => {
    expect(corpoLimpo({ preco: 0, ativo: false, servicos: [] })).toEqual({ preco: 0, ativo: false, servicos: [] })
  })
})

describe('cpfValido', () => {
  it.each(['529.982.247-25', '52998224725', '111.444.777-35'])('aceita %s', (cpf) => {
    expect(cpfValido(cpf)).toBe(true)
  })

  it.each(['529.982.247-24', '111.111.111-11', '123', ''])('recusa %s', (cpf) => {
    expect(cpfValido(cpf)).toBe(false)
  })
})

describe('celularValido', () => {
  it.each(['(69) 99999-1234', '69999991234', '+55 69 99999-1234'])('aceita %s', (celular) => {
    expect(celularValido(celular)).toBe(true)
  })

  it.each(['(69) 3333-1234', '9999-1234', '(01) 99999-1234'])('recusa %s', (celular) => {
    expect(celularValido(celular)).toBe(false)
  })
})

describe('NUMERO_OPCIONAL', () => {
  it('converte vazio em undefined e texto em número', () => {
    expect(NUMERO_OPCIONAL.setValueAs('')).toBeUndefined()
    expect(NUMERO_OPCIONAL.setValueAs('5000')).toBe(5000)
  })
})
