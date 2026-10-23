import { Interruptor } from '@/components/ui/campos'
import { Selo } from '@/components/ui/estrutura'
import type { ConsentimentoDto, ConsentimentoDtoFinalidade } from '@/api/gerado/modelos'
import { formatarDataHora } from '@/lib/formatos'
import { FINALIDADES_CONSENTIMENTO } from '@/lib/formulario'

const FINALIDADES = Object.keys(FINALIDADES_CONSENTIMENTO) as ConsentimentoDtoFinalidade[]

/** Situação atual por finalidade: o histórico pode ter várias linhas, vale a vigente ou a mais recente. */
export function situacaoAtual(consentimentos: ConsentimentoDto[], finalidade: ConsentimentoDtoFinalidade) {
  const daFinalidade = consentimentos.filter((c) => c.finalidade === finalidade)
  return daFinalidade.find((c) => c.vigente) ?? daFinalidade.sort((a, b) => (b.concedidoEm ?? '').localeCompare(a.concedidoEm ?? ''))[0]
}

/**
 * Lista de consentimentos LGPD. Com `aoAlternar`, o próprio titular concede/revoga;
 * sem, é a visão da equipe (somente leitura).
 */
export function Consentimentos({ consentimentos, aoAlternar, ocupado = false }: {
  consentimentos: ConsentimentoDto[]
  aoAlternar?: (finalidade: ConsentimentoDtoFinalidade, conceder: boolean) => void
  ocupado?: boolean
}) {
  return (
    <ul className="flex flex-col divide-y divide-borda">
      {FINALIDADES.map((finalidade) => {
        const { rotulo, descricao } = FINALIDADES_CONSENTIMENTO[finalidade]
        const atual = situacaoAtual(consentimentos, finalidade)
        const vigente = atual?.vigente ?? false
        const quando = vigente ? atual?.concedidoEm : atual?.revogadoEm
        const detalhe = quando && `${vigente ? 'Autorizado' : 'Revogado'} em ${formatarDataHora(quando)}`
        return (
          <li key={finalidade} className="py-3">
            {aoAlternar ? (
              <Interruptor
                rotulo={rotulo}
                descricao={detalhe ? `${descricao} ${detalhe}.` : descricao}
                checked={vigente}
                disabled={ocupado}
                onChange={(e) => aoAlternar(finalidade, e.target.checked)}
              />
            ) : (
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-medium">{rotulo}</p>
                  {detalhe && <p className="text-xs text-texto-3">{detalhe}</p>}
                </div>
                <Selo variante={vigente ? 'preenchido' : 'tracejado'}>{vigente ? 'Autorizado' : 'Não autorizado'}</Selo>
              </div>
            )}
          </li>
        )
      })}
    </ul>
  )
}
