package br.com.clinica.auditoria;

/**
 * API do módulo de auditoria. Deve ser chamada DENTRO da transação do caso de uso: se a operação
 * for desfeita, o registro também é; se a auditoria falhar, a operação não acontece.
 * Ator, clínica, IP e correlation-id são resolvidos automaticamente.
 */
public interface RegistradorAuditoria {

    void registrar(EventoAuditoria evento);
}
