package br.com.clinica.auditoria.application;

import br.com.clinica.compartilhado.application.Pagina;
import br.com.clinica.compartilhado.domain.ClinicaId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída: leitura da trilha com filtros. */
public interface ConsultaAuditoria {

    Pagina<RegistroAuditoria> buscar(ClinicaId clinica, Filtro filtro, Pagina.Pedido pedido);

    record Filtro(Optional<String> recursoTipo, Optional<String> recursoId, Optional<String> atorSub,
                  Optional<UUID> pacienteId, Optional<Instant> de, Optional<Instant> ate) { }

    record RegistroAuditoria(long id, Instant ocorridoEm, String atorSub, String atorPapel, String acao,
                             String recursoTipo, String recursoId, UUID pacienteId, String ip,
                             String correlationId, Map<String, String> detalhes) { }
}
