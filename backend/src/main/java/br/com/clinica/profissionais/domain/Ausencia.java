package br.com.clinica.profissionais.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Período em que o profissional não atende (férias, bloqueio manual ou evento externo do Google). */
public record Ausencia(UUID id, UUID profissionalId, Tipo tipo, Periodo periodo, Optional<String> motivo) {

    public enum Tipo { FERIAS, BLOQUEIO, EXTERNO_GOOGLE }

    private static final Duration DURACAO_MAXIMA = Duration.ofDays(120);

    public Ausencia {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profissionalId, "profissionalId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(periodo, "periodo");
        motivo = motivo == null ? Optional.empty() : motivo.map(String::strip).filter(m -> !m.isEmpty());
        if (Duration.between(periodo.inicio(), periodo.fim()).compareTo(DURACAO_MAXIMA) > 0) {
            throw new DadoInvalidoException("ausencia-longa", "Uma ausência pode durar no máximo 120 dias.");
        }
    }

    public static Ausencia nova(UUID profissional, Tipo tipo, Periodo periodo, Optional<String> motivo) {
        return new Ausencia(UUID.randomUUID(), profissional, tipo, periodo, motivo);
    }
}
