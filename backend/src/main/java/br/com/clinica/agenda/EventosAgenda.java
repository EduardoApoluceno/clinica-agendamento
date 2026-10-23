package br.com.clinica.agenda;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Eventos de domínio da agenda, publicados dentro da transação do caso de uso (o Event Publication
 * Registry do Spring Modulith entrega aos outros módulos depois do commit). Sem dados pessoais:
 * só identificadores e horários.
 */
public final class EventosAgenda {

    private EventosAgenda() {
    }

    /** Todo evento da agenda diz de qual profissional é (avisos de "disponibilidade mudou" via SSE). */
    public sealed interface EventoAgenda permits AgendamentoCriado, AgendamentoConfirmado,
            AgendamentoRemarcado, AgendamentoCancelado, AgendamentoAtualizado, ReservaExpirada, VagaLiberada {
        UUID profissionalId();
    }

    /** @param status RESERVADO quando aguarda pagamento antecipado; senão AGUARDANDO_CONFIRMACAO */
    public record AgendamentoCriado(UUID agendamentoId, UUID profissionalId, UUID pacienteId, UUID servicoId,
                                    Instant inicio, Instant fim, String status, String origem)
            implements EventoAgenda { }

    public record AgendamentoConfirmado(UUID agendamentoId, UUID profissionalId, UUID pacienteId, Instant inicio)
            implements EventoAgenda { }

    public record AgendamentoRemarcado(UUID agendamentoId, UUID profissionalId, UUID pacienteId,
                                       Instant inicioAnterior, Instant fimAnterior, Instant inicio, Instant fim)
            implements EventoAgenda { }

    public record AgendamentoCancelado(UUID agendamentoId, UUID profissionalId, UUID pacienteId, Instant inicio,
                                       Instant fim, boolean pelaEquipe, Optional<String> motivo)
            implements EventoAgenda { }

    /** Mudou o status de um agendamento já existente (presença, falta, conclusão). */
    public record AgendamentoAtualizado(UUID agendamentoId, UUID profissionalId, String status)
            implements EventoAgenda { }

    /** O hold de pagamento venceu sem pagamento (job). */
    public record ReservaExpirada(UUID agendamentoId, UUID profissionalId, UUID pacienteId, Instant inicio)
            implements EventoAgenda { }

    /** Um horário voltou a ficar livre (cancelamento, remarcação, reserva expirada): aciona a lista de espera. */
    public record VagaLiberada(UUID profissionalId, UUID servicoId, Instant inicio, Instant fim)
            implements EventoAgenda { }
}
