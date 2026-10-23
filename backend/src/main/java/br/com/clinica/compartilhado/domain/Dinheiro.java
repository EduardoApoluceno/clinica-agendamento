package br.com.clinica.compartilhado.domain;

/** Valor monetário em centavos de real (sem ponto flutuante). */
public record Dinheiro(long centavos) {

    public static final Dinheiro ZERO = new Dinheiro(0);

    public Dinheiro {
        if (centavos < 0) {
            throw new DadoInvalidoException("valor-negativo", "Valor não pode ser negativo.");
        }
    }

    public boolean maiorQue(Dinheiro outro) {
        return centavos > outro.centavos;
    }
}
