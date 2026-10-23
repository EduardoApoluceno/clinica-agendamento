package br.com.clinica.profissionais.adapters.out.persistence;

import br.com.clinica.compartilhado.domain.ConflitoException;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

/** Regras comuns de gravação: concorrência otimista e tradução de violação de unicidade. */
final class Gravacao {

    private Gravacao() {
    }

    static <E> E exigirVersao(E entidade, Long versaoAtual, long versaoEsperada) {
        if (versaoAtual == null || versaoAtual != versaoEsperada) {
            throw new ConflitoException("edicao-concorrente", "Os dados foram alterados por outra pessoa. Recarregue.");
        }
        return entidade;
    }

    static <E> E gravar(Supplier<E> operacao, String mensagemDuplicidade) {
        try {
            return operacao.get();
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("registro-duplicado", mensagemDuplicidade);
        }
    }
}
