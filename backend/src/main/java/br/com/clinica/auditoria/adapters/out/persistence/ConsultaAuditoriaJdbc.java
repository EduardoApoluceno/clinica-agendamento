package br.com.clinica.auditoria.adapters.out.persistence;

import br.com.clinica.auditoria.application.ConsultaAuditoria;
import br.com.clinica.compartilhado.application.Pagina;
import br.com.clinica.compartilhado.domain.ClinicaId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Consulta parametrizada (sem concatenação de valores) da trilha, sempre restrita à clínica. */
@Component
class ConsultaAuditoriaJdbc implements ConsultaAuditoria {

    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    ConsultaAuditoriaJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Pagina<RegistroAuditoria> buscar(ClinicaId clinica, Filtro filtro, Pagina.Pedido pedido) {
        StringBuilder where = new StringBuilder(" WHERE clinica_id = ?");
        List<Object> parametros = new ArrayList<>(List.of(clinica.valor()));
        filtro.recursoTipo().ifPresent(v -> adicionar(where, parametros, "recurso_tipo = ?", v));
        filtro.recursoId().ifPresent(v -> adicionar(where, parametros, "recurso_id = ?", v));
        filtro.atorSub().ifPresent(v -> adicionar(where, parametros, "ator_sub = ?", v));
        filtro.pacienteId().ifPresent(v -> adicionar(where, parametros, "paciente_id = ?", v));
        filtro.de().ifPresent(v -> adicionar(where, parametros, "ocorrido_em >= ?", Timestamp.from(v)));
        filtro.ate().ifPresent(v -> adicionar(where, parametros, "ocorrido_em < ?", Timestamp.from(v)));

        Long total = jdbc.queryForObject("SELECT count(*) FROM evento_auditoria" + where, Long.class,
                parametros.toArray());
        List<Object> comPaginacao = new ArrayList<>(parametros);
        comPaginacao.add(pedido.tamanho());
        comPaginacao.add(pedido.deslocamento());
        List<RegistroAuditoria> itens = jdbc.query("""
                SELECT id, ocorrido_em, ator_sub, ator_papel, acao, recurso_tipo, recurso_id, paciente_id,
                       host(ip) AS ip, correlation_id, detalhes::text AS detalhes
                FROM evento_auditoria""" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, n) -> mapear(rs, json), comPaginacao.toArray());
        return new Pagina<>(itens, pedido.pagina(), pedido.tamanho(), total == null ? 0 : total);
    }

    private static void adicionar(StringBuilder where, List<Object> parametros, String condicao, Object valor) {
        where.append(" AND ").append(condicao);
        parametros.add(valor);
    }

    private static RegistroAuditoria mapear(ResultSet rs, JsonMapper json) throws SQLException {
        return new RegistroAuditoria(rs.getLong("id"), rs.getTimestamp("ocorrido_em").toInstant(),
                rs.getString("ator_sub"), rs.getString("ator_papel"), rs.getString("acao"),
                rs.getString("recurso_tipo"), rs.getString("recurso_id"), rs.getObject("paciente_id", UUID.class),
                rs.getString("ip"), rs.getString("correlation_id"), lerDetalhes(rs.getString("detalhes"), json));
    }

    private static Map<String, String> lerDetalhes(String detalhes, JsonMapper json) {
        return detalhes == null ? Map.of() : json.readValue(detalhes, MAPA);
    }
}
