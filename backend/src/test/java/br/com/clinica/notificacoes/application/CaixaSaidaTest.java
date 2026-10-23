package br.com.clinica.notificacoes.application;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaixaSaidaTest {

    @Test
    void mensagemExpoeApenasDestinoMascarado() {
        var mensagem = new CaixaSaida.MensagemSaida(UUID.randomUUID(), "WHATSAPP", "(69) *****-1234",
                "Seu código é 123456", Instant.parse("2026-10-22T12:00:00Z"));
        assertThat(mensagem.destino()).doesNotContainPattern("\\d{5}");
        assertThat(mensagem.canal()).isEqualTo("WHATSAPP");
        assertThat(mensagem).isEqualTo(new CaixaSaida.MensagemSaida(mensagem.id(), mensagem.canal(), mensagem.destino(),
                mensagem.conteudo(), mensagem.criadoEm()));
    }
}
