package br.com.clinica.compartilhado.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador da clínica (tenant). Presente em toda operação de negócio. */
public record ClinicaId(UUID valor) {

    public ClinicaId {
        Objects.requireNonNull(valor, "valor");
    }

    public static ClinicaId de(String valor) {
        return new ClinicaId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
