package br.com.clinica.compartilhado.domain;

import java.time.Instant;
import java.util.Objects;

/** Intervalo semiaberto [inicio, fim) em UTC — mesma semântica do tstzrange '[)' no banco. */
public record Periodo(Instant inicio, Instant fim) {

    public Periodo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!fim.isAfter(inicio)) {
            throw new DadoInvalidoException("periodo-invalido", "O fim deve ser posterior ao início.");
        }
    }

    public boolean sobrepoe(Periodo outro) {
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    public boolean contem(Instant instante) {
        return !instante.isBefore(inicio) && instante.isBefore(fim);
    }
}
