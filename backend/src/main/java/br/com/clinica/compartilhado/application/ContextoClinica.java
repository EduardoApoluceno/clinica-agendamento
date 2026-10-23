package br.com.clinica.compartilhado.application;

import br.com.clinica.compartilhado.domain.ClinicaId;

/**
 * Porta: clínica (tenant) da operação corrente. Hoje é uma só, configurada; no SaaS virá do
 * subdomínio/usuário sem que os casos de uso mudem.
 */
public interface ContextoClinica {

    ClinicaId atual();
}
