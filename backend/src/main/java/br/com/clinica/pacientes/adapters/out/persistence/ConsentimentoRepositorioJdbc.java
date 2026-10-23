package br.com.clinica.pacientes.adapters.out.persistence;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.pacientes.application.PacientesPortas.ConsentimentoRepositorio;
import br.com.clinica.pacientes.domain.Consentimento;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ConsentimentoRepositorioJdbc implements ConsentimentoRepositorio {

    private final JdbcTemplate jdbc;

    ConsentimentoRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Consentimento> listar(ClinicaId clinica, UUID paciente) {
        return jdbc.query("""
                SELECT id, paciente_id, finalidade, versao_termo, concedido_em, revogado_em FROM consentimento
                WHERE clinica_id = ? AND paciente_id = ? ORDER BY concedido_em DESC""",
                (rs, n) -> new Consentimento(rs.getObject("id", UUID.class), rs.getObject("paciente_id", UUID.class),
                        Consentimento.Finalidade.valueOf(rs.getString("finalidade")), rs.getString("versao_termo"),
                        rs.getTimestamp("concedido_em").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("revogado_em")).map(Timestamp::toInstant)),
                clinica.valor(), paciente);
    }

    @Override
    public void conceder(ClinicaId clinica, Consentimento c, String origem, Optional<String> ip) {
        jdbc.update("""
                INSERT INTO consentimento (id, clinica_id, paciente_id, finalidade, versao_termo, concedido_em, origem, ip)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::inet)""",
                c.id(), clinica.valor(), c.pacienteId(), c.finalidade().name(), c.versaoTermo(),
                Timestamp.from(c.concedidoEm()), origem, ip.orElse(null));
    }

    @Override
    public boolean revogar(ClinicaId clinica, UUID paciente, Consentimento.Finalidade finalidade, Instant quando) {
        return jdbc.update("""
                UPDATE consentimento SET revogado_em = ?
                WHERE clinica_id = ? AND paciente_id = ? AND finalidade = ? AND revogado_em IS NULL""",
                Timestamp.from(quando), clinica.valor(), paciente, finalidade.name()) > 0;
    }
}
