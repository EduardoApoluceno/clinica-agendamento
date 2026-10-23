interface Props {
  titulo: string
  fase: string
  descricao: string
}

/** Placeholder das telas de referência até a fase em que cada uma é entregue. */
export function EmConstrucao({ titulo, fase, descricao }: Props) {
  return (
    <section aria-labelledby="titulo-pagina" className="mx-auto max-w-3xl">
      <h1 id="titulo-pagina" className="text-2xl font-semibold">
        {titulo}
      </h1>
      <div className="mt-6 rounded-cartao border border-dashed border-borda-forte bg-superficie p-6">
        <p className="text-sm font-medium text-texto-2">Entrega prevista: {fase}</p>
        <p className="mt-2 text-sm text-texto-3">{descricao}</p>
      </div>
    </section>
  )
}
