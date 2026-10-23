package br.com.clinica.agenda.adapters.in.web;

import br.com.clinica.agenda.application.ConsultarAgenda.Item;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.OrigemAgendamento;
import br.com.clinica.agenda.domain.StatusAgendamento;
import java.time.Instant;
import java.util.UUID;

/** Agendamento como a API devolve; {@code versao} volta na remarcação (concorrência otimista). */
record AgendamentoDto(UUID id, UUID profissionalId, String profissional, UUID pacienteId, String paciente,
                      UUID servicoId, String servico, Instant inicio, Instant fim, StatusAgendamento status,
                      OrigemAgendamento origem, Instant reservaExpiraEm, long precoCentavos,
                      long valorAntecipadoCentavos, String observacao, Instant canceladoEm, String motivoCancelamento,
                      long versao) {

    static AgendamentoDto de(Item item) {
        Agendamento a = item.agendamento();
        return new AgendamentoDto(a.id(), a.profissionalId(), item.profissional(), a.pacienteId(), item.paciente(),
                a.servicoId(), item.servico(), a.periodo().inicio(), a.periodo().fim(), a.status(), a.origem(),
                a.holdExpiraEm().orElse(null), a.preco().centavos(), a.valorAntecipado().centavos(),
                a.observacao().orElse(null), a.cancelamento().map(Agendamento.Cancelamento::em).orElse(null),
                a.cancelamento().flatMap(Agendamento.Cancelamento::motivo).orElse(null), a.versao());
    }
}
