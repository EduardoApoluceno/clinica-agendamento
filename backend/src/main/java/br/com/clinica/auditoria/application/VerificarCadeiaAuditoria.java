package br.com.clinica.auditoria.application;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso: recalcula a cadeia de hashes e acusa adulteração. O log em nível ERROR com o
 * marcador {@code AUDITORIA_CORROMPIDA} alimenta o alerta de observabilidade.
 */
public class VerificarCadeiaAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(VerificarCadeiaAuditoria.class);

    private final CadeiaAuditoria cadeia;

    public VerificarCadeiaAuditoria(CadeiaAuditoria cadeia) {
        this.cadeia = cadeia;
    }

    public Optional<Long> executar() {
        Optional<Long> corrompido = cadeia.primeiroRegistroCorrompido();
        corrompido.ifPresentOrElse(
                id -> LOG.error("AUDITORIA_CORROMPIDA: cadeia de hash quebrada a partir do registro {}", id),
                () -> LOG.info("Trilha de auditoria íntegra ({} registros)", cadeia.totalRegistros()));
        return corrompido;
    }
}
