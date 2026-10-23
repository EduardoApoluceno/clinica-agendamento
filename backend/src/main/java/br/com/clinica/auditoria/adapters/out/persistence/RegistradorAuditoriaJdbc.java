package br.com.clinica.auditoria.adapters.out.persistence;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.Ator;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.web.CorrelationIdFilter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava a trilha append-only com hash encadeado. Um advisory lock transacional serializa a cadeia:
 * cada registro aponta para o hash do anterior já confirmado.
 */
@Component
class RegistradorAuditoriaJdbc implements RegistradorAuditoria {

    private static final int TAMANHO_USER_AGENT = 300;

    private static final String SQL_INSERIR = """
            INSERT INTO evento_auditoria (clinica_id, ocorrido_em, ator_sub, ator_papel, acao, recurso_tipo,
                recurso_id, paciente_id, ip, user_agent, correlation_id, detalhes, hash_anterior, hash)
            SELECT v.clinica_id, v.ocorrido_em, v.ator_sub, v.ator_papel, v.acao, v.recurso_tipo, v.recurso_id,
                   v.paciente_id, v.ip, v.user_agent, v.correlation_id, v.detalhes, u.hash,
                   fn_hash_auditoria(u.hash, v.clinica_id, v.ocorrido_em, v.ator_sub, v.ator_papel, v.acao,
                       v.recurso_tipo, v.recurso_id, v.paciente_id, v.ip, v.user_agent, v.correlation_id, v.detalhes)
            FROM (SELECT ?::uuid AS clinica_id, ?::timestamptz AS ocorrido_em, ?::text AS ator_sub,
                         ?::text AS ator_papel, ?::text AS acao, ?::text AS recurso_tipo, ?::text AS recurso_id,
                         ?::uuid AS paciente_id, ?::inet AS ip, ?::text AS user_agent, ?::text AS correlation_id,
                         ?::jsonb AS detalhes) v
            CROSS JOIN (SELECT (SELECT hash FROM evento_auditoria ORDER BY id DESC LIMIT 1) AS hash) u
            """;

    private final JdbcTemplate jdbc;
    private final ProvedorAtor provedorAtor;
    private final ContextoClinica contextoClinica;
    private final Clock relogio;
    private final JsonMapper json;

    RegistradorAuditoriaJdbc(JdbcTemplate jdbc, ProvedorAtor provedorAtor, ContextoClinica contextoClinica,
                             Clock relogio, JsonMapper json) {
        this.jdbc = jdbc;
        this.provedorAtor = provedorAtor;
        this.contextoClinica = contextoClinica;
        this.relogio = relogio;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void registrar(EventoAuditoria evento) {
        Ator ator = provedorAtor.atual();
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtext('evento_auditoria'))");
        jdbc.update(SQL_INSERIR,
                contextoClinica.atual().valor(),
                Timestamp.from(relogio.instant().truncatedTo(ChronoUnit.MICROS)),
                ator.sub(),
                ator.papelPrincipal().map(Enum::name).orElse(null),
                evento.acao(),
                evento.recursoTipo(),
                evento.recursoId().orElse(null),
                evento.pacienteId().orElse(null),
                ator.ip().orElse(null),
                ator.userAgent().map(RegistradorAuditoriaJdbc::limitar).orElse(null),
                MDC.get(CorrelationIdFilter.CHAVE_MDC),
                json.writeValueAsString(evento.detalhes()));
    }

    private static String limitar(String userAgent) {
        return userAgent.length() <= TAMANHO_USER_AGENT ? userAgent : userAgent.substring(0, TAMANHO_USER_AGENT);
    }
}
