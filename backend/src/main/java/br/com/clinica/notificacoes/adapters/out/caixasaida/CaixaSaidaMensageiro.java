package br.com.clinica.notificacoes.adapters.out.caixasaida;

import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.Telefone;
import br.com.clinica.notificacoes.EnvioMensagens;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * "FakeMessageSender" do modo demonstração: em vez de enviar, grava na caixa de saída exibida no
 * painel. Destino guardado apenas mascarado.
 */
@Component
@ConditionalOnProperty(name = "app.notificacoes.provedor", havingValue = "caixa-saida", matchIfMissing = true)
class CaixaSaidaMensageiro implements EnvioMensagens {

    private final JdbcTemplate jdbc;
    private final ContextoClinica contexto;
    private final Clock relogio;

    CaixaSaidaMensageiro(JdbcTemplate jdbc, ContextoClinica contexto, Clock relogio) {
        this.jdbc = jdbc;
        this.contexto = contexto;
        this.relogio = relogio;
    }

    @Override
    public void enviarCodigoVerificacao(Telefone destino, String codigo) {
        String texto = "Seu código de verificação é " + codigo + ". Ele expira em 10 minutos. "
                + "Não compartilhe este código com ninguém.";
        jdbc.update("INSERT INTO mensagem_saida (id, clinica_id, canal, destino_mascarado, conteudo, criado_em) "
                        + "VALUES (?, ?, 'WHATSAPP', ?, ?, ?)",
                UUID.randomUUID(), contexto.atual().valor(), destino.mascarado(), texto, Timestamp.from(relogio.instant()));
    }
}
