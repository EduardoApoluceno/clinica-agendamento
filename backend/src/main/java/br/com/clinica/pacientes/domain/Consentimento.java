package br.com.clinica.pacientes.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Registro LGPD de consentimento para uma finalidade, com a versão do termo aceito. Revogar não
 * apaga: grava a data de revogação (histórico auditável).
 */
public record Consentimento(UUID id, UUID pacienteId, Finalidade finalidade, String versaoTermo,
                            Instant concedidoEm, Optional<Instant> revogadoEm) {

    /** Finalidades de tratamento de dados que dependem de consentimento. */
    public enum Finalidade {
        TRATAMENTO_SAUDE, LEMBRETES_WHATSAPP, LEMBRETES_EMAIL, LEMBRETES_SMS, PESQUISA_SATISFACAO, COMUNICACOES_CLINICA
    }

    public Consentimento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pacienteId, "pacienteId");
        Objects.requireNonNull(finalidade, "finalidade");
        Objects.requireNonNull(versaoTermo, "versaoTermo");
        Objects.requireNonNull(concedidoEm, "concedidoEm");
        revogadoEm = revogadoEm == null ? Optional.empty() : revogadoEm;
    }

    public boolean vigente() {
        return revogadoEm.isEmpty();
    }
}
