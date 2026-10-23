package br.com.clinica.auditoria.application;

import java.util.Optional;

/** Porta de saída: verificação da integridade da trilha. */
public interface CadeiaAuditoria {

    /** Id do primeiro registro cujo hash não confere com o recalculado, se houver. */
    Optional<Long> primeiroRegistroCorrompido();

    long totalRegistros();
}
