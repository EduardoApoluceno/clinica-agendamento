package br.com.clinica.notificacoes.application;

import br.com.clinica.compartilhado.domain.ClinicaId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Porta de saída: mensagens registradas pelo adaptador de demonstração. */
public interface CaixaSaida {

    List<MensagemSaida> recentes(ClinicaId clinica, int limite);

    record MensagemSaida(UUID id, String canal, String destino, String conteudo, Instant criadoEm) { }
}
