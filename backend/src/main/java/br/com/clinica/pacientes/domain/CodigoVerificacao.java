package br.com.clinica.pacientes.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Desafio de verificação de contato. O código em claro nunca é persistido (só o HMAC).
 *
 * @param tentativas confirmações erradas já feitas
 */
public record CodigoVerificacao(UUID id, UUID pacienteId, Contato destino, int tentativas, Instant expiraEm,
                                Optional<Instant> usadoEm) {

    public static final Duration VALIDADE = Duration.ofMinutes(10);
    public static final int MAXIMO_TENTATIVAS = 5;
    public static final int MAXIMO_ENVIOS_POR_HORA = 3;

    public CodigoVerificacao {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destino, "destino");
        usadoEm = usadoEm == null ? Optional.empty() : usadoEm;
    }

    public boolean utilizavel(Instant agora) {
        return usadoEm.isEmpty() && agora.isBefore(expiraEm) && tentativas < MAXIMO_TENTATIVAS;
    }
}
