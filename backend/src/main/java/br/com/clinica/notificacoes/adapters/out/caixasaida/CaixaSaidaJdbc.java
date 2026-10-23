package br.com.clinica.notificacoes.adapters.out.caixasaida;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.notificacoes.application.CaixaSaida;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class CaixaSaidaJdbc implements CaixaSaida {

    private final JdbcTemplate jdbc;

    CaixaSaidaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MensagemSaida> recentes(ClinicaId clinica, int limite) {
        return jdbc.query("SELECT id, canal, destino_mascarado, conteudo, criado_em FROM mensagem_saida "
                        + "WHERE clinica_id = ? ORDER BY criado_em DESC LIMIT ?",
                (rs, n) -> new MensagemSaida(rs.getObject("id", UUID.class), rs.getString("canal"),
                        rs.getString("destino_mascarado"), rs.getString("conteudo"),
                        rs.getTimestamp("criado_em").toInstant()),
                clinica.valor(), limite);
    }
}
