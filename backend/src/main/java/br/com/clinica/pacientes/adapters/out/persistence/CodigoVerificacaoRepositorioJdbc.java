package br.com.clinica.pacientes.adapters.out.persistence;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.infrastructure.criptografia.CifradorCampos;
import br.com.clinica.pacientes.application.PacientesPortas.CodigoComHash;
import br.com.clinica.pacientes.application.PacientesPortas.CodigoVerificacaoRepositorio;
import br.com.clinica.pacientes.domain.CodigoVerificacao;
import br.com.clinica.pacientes.domain.Contato;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class CodigoVerificacaoRepositorioJdbc implements CodigoVerificacaoRepositorio {

    private final JdbcTemplate jdbc;
    private final CifradorCampos cifrador;

    CodigoVerificacaoRepositorioJdbc(JdbcTemplate jdbc, CifradorCampos cifrador) {
        this.jdbc = jdbc;
        this.cifrador = cifrador;
    }

    @Override
    public void salvar(ClinicaId clinica, CodigoVerificacao c, byte[] codigoHash, Instant criadoEm) {
        jdbc.update("""
                INSERT INTO codigo_verificacao (id, clinica_id, paciente_id, destino_hmac, destino_cifrado, codigo_hmac,
                    tentativas, expira_em, criado_em) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)""",
                c.id(), clinica.valor(), c.pacienteId(), cifrador.hmac(c.destino().valor()),
                cifrador.cifrar(c.destino().valor()), codigoHash, Timestamp.from(c.expiraEm()), Timestamp.from(criadoEm));
    }

    @Override
    public Optional<CodigoComHash> ultimoPendente(ClinicaId clinica, UUID paciente) {
        return jdbc.query("""
                SELECT id, paciente_id, destino_cifrado, codigo_hmac, tentativas, expira_em, usado_em
                FROM codigo_verificacao WHERE clinica_id = ? AND paciente_id = ? AND usado_em IS NULL
                ORDER BY criado_em DESC LIMIT 1 FOR UPDATE""",
                (rs, n) -> new CodigoComHash(new CodigoVerificacao(rs.getObject("id", UUID.class),
                        rs.getObject("paciente_id", UUID.class),
                        new Contato(Contato.Tipo.CELULAR, cifrador.decifrar(rs.getBytes("destino_cifrado")), true,
                                Optional.empty()),
                        rs.getInt("tentativas"), rs.getTimestamp("expira_em").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("usado_em")).map(Timestamp::toInstant)),
                        rs.getBytes("codigo_hmac")),
                clinica.valor(), paciente).stream().findFirst();
    }

    @Override
    public int enviosDesde(ClinicaId clinica, UUID paciente, Instant desde) {
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM codigo_verificacao WHERE clinica_id = ? AND paciente_id = ? AND criado_em >= ?""",
                Integer.class, clinica.valor(), paciente, Timestamp.from(desde));
        return total == null ? 0 : total;
    }

    @Override
    public void registrarTentativa(UUID codigo) {
        jdbc.update("UPDATE codigo_verificacao SET tentativas = tentativas + 1 WHERE id = ?", codigo);
    }

    @Override
    public void marcarUsado(UUID codigo, Instant quando) {
        jdbc.update("UPDATE codigo_verificacao SET usado_em = ? WHERE id = ?", Timestamp.from(quando), codigo);
    }
}
