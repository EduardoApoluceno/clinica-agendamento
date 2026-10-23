package br.com.clinica.agenda.application;

import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.StatusAgendamento;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Portas de saída do módulo agenda. Toda consulta é restrita à clínica informada. */
public final class AgendaPortas {

    private AgendaPortas() {
    }

    public interface AgendamentoRepositorio {
        Optional<Agendamento> buscar(ClinicaId clinica, UUID id);

        /**
         * Serializa, até o fim da transação, quem altera a agenda de um profissional (agendamento ×
         * ausência, ADR-0004). A constraint do banco continua sendo a autoridade final.
         */
        void travarAgendaDoProfissional(ClinicaId clinica, UUID profissional);

        /** Lança HorarioIndisponivelException se o período colidir com outro que ocupa o horário. */
        void inserir(Agendamento agendamento);

        /**
         * Grava a nova versão se {@code agendamento.versao()} ainda for a do banco (senão ConflitoException);
         * lança HorarioIndisponivelException se uma remarcação colidir.
         */
        Agendamento atualizar(Agendamento agendamento);

        void registrarHistorico(ClinicaId clinica, Transicao transicao);

        /** Agendamentos que ocupam horário do profissional na janela (inclui holds ainda não expirados pelo job). */
        List<Agendamento> ocupando(ClinicaId clinica, UUID profissional, Periodo janela);

        /** Todos os agendamentos que começam na janela (opcionalmente de um profissional), qualquer status. */
        List<Agendamento> iniciandoEm(ClinicaId clinica, Optional<UUID> profissional, Periodo janela);

        List<Agendamento> doPaciente(ClinicaId clinica, UUID paciente, int limite);

        /** Holds vencidos de todas as clínicas, para o job de expiração. */
        List<Agendamento> reservasVencidas(Instant agora, int limite);
    }

    /** Uma linha do histórico do agendamento (append-only). */
    public record Transicao(UUID agendamentoId, Optional<StatusAgendamento> statusAnterior,
                            StatusAgendamento statusNovo, Optional<Periodo> periodoAnterior,
                            Optional<Periodo> periodoNovo, String ator, Optional<String> motivo, Instant em) { }
}
