package br.com.clinica.agenda.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agendamento (raiz do agregado). Cada transição devolve uma nova instância; quem pode fazer o quê
 * (paciente × equipe, antecedência) é decidido no caso de uso.
 *
 * @param holdExpiraEm presente só em RESERVADO (aguardando pagamento antecipado)
 * @param valorAntecipado sinal ou valor integral cobrado antes; zero quando o serviço não exige
 */
public record Agendamento(UUID id, ClinicaId clinicaId, UUID profissionalId, UUID pacienteId, UUID servicoId,
                          Periodo periodo, StatusAgendamento status, OrigemAgendamento origem,
                          Optional<Instant> holdExpiraEm, Dinheiro preco, Dinheiro valorAntecipado,
                          Optional<String> observacao, Optional<Cancelamento> cancelamento, String criadoPor,
                          long versao) {

    private static final int TAMANHO_MAXIMO_OBSERVACAO = 500;
    private static final int TAMANHO_MAXIMO_MOTIVO = 200;

    public Agendamento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clinicaId, "clinicaId");
        Objects.requireNonNull(profissionalId, "profissionalId");
        Objects.requireNonNull(pacienteId, "pacienteId");
        Objects.requireNonNull(servicoId, "servicoId");
        Objects.requireNonNull(periodo, "periodo");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(origem, "origem");
        Objects.requireNonNull(preco, "preco");
        Objects.requireNonNull(valorAntecipado, "valorAntecipado");
        Objects.requireNonNull(criadoPor, "criadoPor");
        holdExpiraEm = holdExpiraEm == null ? Optional.empty() : holdExpiraEm;
        cancelamento = cancelamento == null ? Optional.empty() : cancelamento;
        observacao = textoOpcional(observacao, TAMANHO_MAXIMO_OBSERVACAO, "observacao-longa");
        if ((status == StatusAgendamento.RESERVADO) != holdExpiraEm.isPresent()) {
            throw new IllegalArgumentException("hold só existe no status RESERVADO");
        }
    }

    /** Exige pagamento antecipado → RESERVADO com prazo; senão → AGUARDANDO_CONFIRMACAO. */
    public static Agendamento novo(Dados dados, Instant agora, Duration duracaoHold) {
        boolean exigePagamento = dados.valorAntecipado().centavos() > 0;
        return new Agendamento(UUID.randomUUID(), dados.clinica(), dados.profissional(), dados.paciente(),
                dados.servico(), dados.periodo(),
                exigePagamento ? StatusAgendamento.RESERVADO : StatusAgendamento.AGUARDANDO_CONFIRMACAO,
                dados.origem(), exigePagamento ? Optional.of(agora.plus(duracaoHold)) : Optional.empty(),
                dados.preco(), dados.valorAntecipado(), dados.observacao(), Optional.empty(), dados.criadoPor(), 0);
    }

    public Agendamento remarcadoPara(Periodo novo) {
        exigir(status.pendente(), "Só é possível remarcar um agendamento que ainda não aconteceu.");
        return com(novo, status, holdExpiraEm, cancelamento);
    }

    public Agendamento cancelado(Instant agora, Optional<String> motivo) {
        exigir(status.pendente(), "Este agendamento não pode mais ser cancelado.");
        Optional<String> motivoLimpo = textoOpcional(motivo, TAMANHO_MAXIMO_MOTIVO, "motivo-longo");
        return com(periodo, StatusAgendamento.CANCELADO, Optional.empty(),
                Optional.of(new Cancelamento(agora, motivoLimpo)));
    }

    /** Confirmar duas vezes não é erro (o paciente pode clicar no lembrete de novo). */
    public Agendamento confirmado() {
        if (status == StatusAgendamento.CONFIRMADO) {
            return this;
        }
        exigir(status == StatusAgendamento.AGUARDANDO_CONFIRMACAO, "Este agendamento não aguarda confirmação.");
        return com(periodo, StatusAgendamento.CONFIRMADO, Optional.empty(), cancelamento);
    }

    /** Pagamento antecipado aprovado (Fase 4): o hold vira agendamento confirmado. */
    public Agendamento pagamentoConfirmado() {
        exigir(status == StatusAgendamento.RESERVADO, "Este agendamento não aguarda pagamento.");
        return com(periodo, StatusAgendamento.CONFIRMADO, Optional.empty(), cancelamento);
    }

    /** Check-in: só no dia do atendimento (no fuso da clínica). */
    public Agendamento presente(Instant agora, ZoneId fuso) {
        exigir(EnumSet.of(StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO).contains(status),
                "A presença só pode ser registrada em agendamentos confirmados ou aguardando confirmação.");
        exigir(agora.atZone(fuso).toLocalDate().equals(periodo.inicio().atZone(fuso).toLocalDate()),
                "A presença só pode ser registrada no dia do atendimento.");
        return com(periodo, StatusAgendamento.PRESENTE, Optional.empty(), cancelamento);
    }

    public Agendamento falta(Instant agora) {
        exigir(EnumSet.of(StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO).contains(status),
                "A falta só pode ser registrada em agendamentos confirmados ou aguardando confirmação.");
        exigir(!agora.isBefore(periodo.inicio()), "A falta só pode ser registrada depois do horário marcado.");
        return com(periodo, StatusAgendamento.FALTA, Optional.empty(), cancelamento);
    }

    public Agendamento realizado() {
        exigir(status == StatusAgendamento.PRESENTE, "Só um atendimento com presença registrada pode ser concluído.");
        return com(periodo, StatusAgendamento.REALIZADO, Optional.empty(), cancelamento);
    }

    public boolean reservaVencida(Instant agora) {
        return holdExpiraEm.filter(expira -> !agora.isBefore(expira)).isPresent();
    }

    public Agendamento expirado(Instant agora) {
        exigir(reservaVencida(agora), "A reserva ainda está dentro do prazo.");
        return com(periodo, StatusAgendamento.EXPIRADO, Optional.empty(), cancelamento);
    }

    private Agendamento com(Periodo novoPeriodo, StatusAgendamento novoStatus, Optional<Instant> novoHold,
                            Optional<Cancelamento> novoCancelamento) {
        return new Agendamento(id, clinicaId, profissionalId, pacienteId, servicoId, novoPeriodo, novoStatus, origem,
                novoHold, preco, valorAntecipado, observacao, novoCancelamento, criadoPor, versao);
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new ConflitoException("transicao-invalida", mensagem);
        }
    }

    private static Optional<String> textoOpcional(Optional<String> texto, int maximo, String codigo) {
        Optional<String> limpo = texto == null ? Optional.empty() : texto.map(String::strip).filter(t -> !t.isEmpty());
        if (limpo.filter(t -> t.length() > maximo).isPresent()) {
            throw new DadoInvalidoException(codigo, "Texto longo demais (máximo " + maximo + " caracteres).");
        }
        return limpo;
    }

    /** Dados de um agendamento novo, já validados contra serviço, profissional e paciente. */
    public record Dados(ClinicaId clinica, UUID profissional, UUID paciente, UUID servico, Periodo periodo,
                        OrigemAgendamento origem, Dinheiro preco, Dinheiro valorAntecipado,
                        Optional<String> observacao, String criadoPor) { }

    public record Cancelamento(Instant em, Optional<String> motivo) { }
}
