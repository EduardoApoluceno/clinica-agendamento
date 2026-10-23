package br.com.clinica.auditoria.adapters.out.persistence;

import br.com.clinica.auditoria.application.CadeiaAuditoria;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class CadeiaAuditoriaJdbc implements CadeiaAuditoria {

    private static final String SQL_PRIMEIRO_CORROMPIDO = """
            SELECT id FROM (
                SELECT id, hash, hash_anterior, lag(hash) OVER (ORDER BY id) AS hash_esperado_anterior,
                       fn_hash_auditoria(lag(hash) OVER (ORDER BY id), clinica_id, ocorrido_em, ator_sub, ator_papel,
                           acao, recurso_tipo, recurso_id, paciente_id, ip, user_agent, correlation_id, detalhes)
                           AS hash_recalculado
                FROM evento_auditoria) c
            WHERE hash <> hash_recalculado OR hash_anterior IS DISTINCT FROM hash_esperado_anterior
            ORDER BY id LIMIT 1
            """;

    private final JdbcTemplate jdbc;

    CadeiaAuditoriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> primeiroRegistroCorrompido() {
        return jdbc.queryForList(SQL_PRIMEIRO_CORROMPIDO, Long.class).stream().findFirst();
    }

    @Override
    public long totalRegistros() {
        Long total = jdbc.queryForObject("SELECT count(*) FROM evento_auditoria", Long.class);
        return total == null ? 0 : total;
    }
}
