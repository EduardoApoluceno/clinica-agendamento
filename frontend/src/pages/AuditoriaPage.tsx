import { useState, type FormEvent } from 'react'
import { keepPreviousData } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Campo, Input } from '@/components/ui/campos'
import { Carregando, Cartao, Tabela, Td, Th, TituloPagina, Vazio } from '@/components/ui/estrutura'
import { useAuditoriaBuscar } from '@/api/gerado/auditoria/auditoria'
import type { AuditoriaBuscarParams } from '@/api/gerado/modelos'
import { formatarDataHora, localClinicaParaUtc, mensagemErro } from '@/lib/formatos'
import { corpoLimpo } from '@/lib/formulario'

const TAMANHO_PAGINA = 50
const FILTRO_VAZIO = { recursoTipo: '', recursoId: '', ator: '', pacienteId: '', de: '', ate: '' }

/** Trilha de auditoria (somente ADMIN): quem fez o quê, quando e de onde. */
export function AuditoriaPage() {
  const [rascunho, setRascunho] = useState(FILTRO_VAZIO)
  const [filtro, setFiltro] = useState<AuditoriaBuscarParams>({})
  const [pagina, setPagina] = useState(0)
  const registros = useAuditoriaBuscar({ ...filtro, pagina, tamanho: TAMANHO_PAGINA }, { query: { placeholderData: keepPreviousData } })
  const total = registros.data?.total ?? 0
  const ultimaPagina = Math.max(0, Math.ceil(total / TAMANHO_PAGINA) - 1)

  const aplicar = (ev: FormEvent) => {
    ev.preventDefault()
    const { de, ate, ...textos } = rascunho
    setFiltro(corpoLimpo({
      ...textos,
      de: de ? localClinicaParaUtc(de) : undefined,
      ate: ate ? localClinicaParaUtc(ate) : undefined,
    }))
    setPagina(0)
  }
  const campo = (nome: keyof typeof FILTRO_VAZIO) => ({
    value: rascunho[nome],
    onChange: (e: { target: { value: string } }) => setRascunho((r) => ({ ...r, [nome]: e.target.value })),
  })

  return (
    <div className="mx-auto max-w-6xl">
      <TituloPagina titulo="Auditoria" descricao="Registros encadeados por hash; a integridade é verificada diariamente." />
      <Cartao className="mb-4">
        <form onSubmit={aplicar} className="grid gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <Campo rotulo="Tipo de recurso">{(p) => <Input {...p} placeholder="PACIENTE" {...campo('recursoTipo')} />}</Campo>
          <Campo rotulo="ID do recurso">{(p) => <Input {...p} spellCheck={false} {...campo('recursoId')} />}</Campo>
          <Campo rotulo="Ator (sub)">{(p) => <Input {...p} spellCheck={false} {...campo('ator')} />}</Campo>
          <Campo rotulo="ID do paciente">{(p) => <Input {...p} spellCheck={false} {...campo('pacienteId')} />}</Campo>
          <Campo rotulo="De">{(p) => <Input {...p} type="datetime-local" {...campo('de')} />}</Campo>
          <Campo rotulo="Até">{(p) => <Input {...p} type="datetime-local" {...campo('ate')} />}</Campo>
          <div className="flex justify-end gap-2 sm:col-span-3 lg:col-span-6">
            <Button type="button" variante="fantasma" onClick={() => { setRascunho(FILTRO_VAZIO); setFiltro({}); setPagina(0) }}>
              Limpar
            </Button>
            <Button type="submit">Filtrar</Button>
          </div>
        </form>
      </Cartao>

      {registros.isPending ? (
        <Carregando />
      ) : registros.isError ? (
        <p role="alert" className="text-sm text-texto-2">{mensagemErro(registros.error)}</p>
      ) : !registros.data.itens?.length ? (
        <Vazio titulo="Nenhum registro" descricao="Ajuste os filtros." />
      ) : (
        <>
          <Tabela rotulo="Registros de auditoria">
            <thead>
              <tr><Th>Quando</Th><Th>Ação</Th><Th>Recurso</Th><Th>Ator</Th><Th>Origem</Th></tr>
            </thead>
            <tbody>
              {registros.data.itens.map((r) => (
                <tr key={r.id}>
                  <Td className="whitespace-nowrap numeros-tabulares">{r.ocorridoEm && formatarDataHora(r.ocorridoEm)}</Td>
                  <Td className="font-medium">{r.acao}</Td>
                  <Td>
                    {r.recursoTipo}
                    {r.recursoId && <span className="block font-mono text-xs text-texto-3">{r.recursoId}</span>}
                  </Td>
                  <Td>
                    {r.atorPapel ?? '—'}
                    {r.atorSub && <span className="block font-mono text-xs text-texto-3">{r.atorSub}</span>}
                  </Td>
                  <Td className="font-mono text-xs text-texto-3">{r.ip}</Td>
                </tr>
              ))}
            </tbody>
          </Tabela>
          <nav aria-label="Paginação" className="mt-3 flex items-center justify-between text-sm text-texto-2">
            <span className="numeros-tabulares">{total} registros</span>
            <div className="flex gap-2">
              <Button variante="secundario" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>Anterior</Button>
              <Button variante="secundario" disabled={pagina >= ultimaPagina} onClick={() => setPagina((p) => p + 1)}>Próxima</Button>
            </div>
          </nav>
        </>
      )}
    </div>
  )
}
