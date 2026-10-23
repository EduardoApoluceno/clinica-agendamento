package br.com.clinica.profissionais.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import java.util.Objects;
import java.util.Optional;

/** Cobrança antecipada opcional do serviço: nenhuma, sinal (valor fixo) ou integral. */
public record PoliticaPagamento(Tipo tipo, Optional<Dinheiro> valorSinal) {

    public enum Tipo { NENHUM, SINAL, INTEGRAL }

    public PoliticaPagamento {
        Objects.requireNonNull(tipo, "tipo");
        valorSinal = valorSinal == null ? Optional.empty() : valorSinal;
        if ((tipo == Tipo.SINAL) != valorSinal.isPresent()) {
            throw new DadoInvalidoException("sinal-invalido", "Informe o valor do sinal apenas para a política SINAL.");
        }
        if (valorSinal.filter(v -> v.centavos() == 0).isPresent()) {
            throw new DadoInvalidoException("sinal-invalido", "O sinal deve ser maior que zero.");
        }
    }

    public static PoliticaPagamento nenhuma() {
        return new PoliticaPagamento(Tipo.NENHUM, Optional.empty());
    }

    /** Valor a cobrar antecipadamente para um serviço de determinado preço. */
    public Dinheiro valorAntecipado(Dinheiro preco) {
        return switch (tipo) {
            case NENHUM -> Dinheiro.ZERO;
            case SINAL -> valorSinal.orElseThrow();
            case INTEGRAL -> preco;
        };
    }
}
