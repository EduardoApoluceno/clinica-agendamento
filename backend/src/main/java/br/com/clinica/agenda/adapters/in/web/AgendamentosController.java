package br.com.clinica.agenda.adapters.in.web;

import br.com.clinica.agenda.application.AgendarConsulta;
import br.com.clinica.agenda.application.ConsultarAgenda;
import br.com.clinica.agenda.application.GerirAgendamento;
import br.com.clinica.agenda.domain.Agendamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agendamentos. Os {@code @PreAuthorize} filtram por perfil; quem pode agir em QUAL agendamento
 * (o próprio paciente, o profissional do atendimento) é decidido nos casos de uso.
 */
@RestController
class AgendamentosController {

    private static final DateTimeFormatter ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final AgendarConsulta agendar;
    private final GerirAgendamento gerir;
    private final ConsultarAgenda consultar;

    AgendamentosController(AgendarConsulta agendar, GerirAgendamento gerir, ConsultarAgenda consultar) {
        this.agendar = agendar;
        this.gerir = gerir;
        this.consultar = consultar;
    }

    @PostMapping("/api/v1/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('agenda:escrever', 'agendamento-proprio:gerir')")
    AgendamentoDto agendar(@Valid @RequestBody NovoAgendamento req) {
        Agendamento novo = agendar.agendar(new AgendarConsulta.Pedido(req.servicoId(), req.profissionalId(),
                req.inicio(), Optional.ofNullable(req.pacienteId()), Optional.ofNullable(req.observacao())));
        return detalhe(novo.id());
    }

    @GetMapping("/api/v1/agendamentos/{id}")
    @PreAuthorize("hasAnyAuthority('agenda:ler', 'agenda-propria:gerir', 'agendamento-proprio:gerir')")
    AgendamentoDto detalhe(@PathVariable UUID id) {
        return AgendamentoDto.de(consultar.detalhe(id));
    }

    @GetMapping("/api/v1/me/agendamentos")
    @PreAuthorize("hasAuthority('agendamento-proprio:gerir')")
    List<AgendamentoDto> meus() {
        return consultar.meus().stream().map(AgendamentoDto::de).toList();
    }

    @PostMapping("/api/v1/agendamentos/{id}/remarcacao")
    @PreAuthorize("hasAnyAuthority('agenda:escrever', 'agendamento-proprio:gerir')")
    AgendamentoDto remarcar(@PathVariable UUID id, @Valid @RequestBody Remarcacao req) {
        gerir.remarcar(id, req.inicio(), req.versao());
        return detalhe(id);
    }

    @PostMapping("/api/v1/agendamentos/{id}/cancelamento")
    @PreAuthorize("hasAnyAuthority('agenda:escrever', 'agendamento-proprio:gerir')")
    AgendamentoDto cancelar(@PathVariable UUID id, @Valid @RequestBody(required = false) Cancelamento req) {
        gerir.cancelar(id, Optional.ofNullable(req).map(Cancelamento::motivo));
        return detalhe(id);
    }

    @PostMapping("/api/v1/agendamentos/{id}/confirmacao")
    @PreAuthorize("hasAnyAuthority('agenda:escrever', 'agendamento-proprio:gerir')")
    AgendamentoDto confirmar(@PathVariable UUID id) {
        gerir.confirmar(id);
        return detalhe(id);
    }

    @PostMapping("/api/v1/agendamentos/{id}/presenca")
    @PreAuthorize("hasAnyAuthority('agenda:escrever', 'agenda-propria:gerir')")
    AgendamentoDto presenca(@PathVariable UUID id) {
        gerir.registrarPresenca(id);
        return detalhe(id);
    }

    @PostMapping("/api/v1/agendamentos/{id}/falta")
    @PreAuthorize("hasAnyAuthority('agenda:escrever', 'agenda-propria:gerir')")
    AgendamentoDto falta(@PathVariable UUID id) {
        gerir.registrarFalta(id);
        return detalhe(id);
    }

    @PostMapping("/api/v1/agendamentos/{id}/conclusao")
    @PreAuthorize("hasAnyAuthority('agenda:escrever', 'agenda-propria:gerir')")
    AgendamentoDto concluir(@PathVariable UUID id) {
        gerir.concluir(id);
        return detalhe(id);
    }

    /** "Adicionar à agenda" (Google, Apple, Outlook): arquivo iCalendar só com serviço e profissional. */
    @GetMapping(path = "/api/v1/agendamentos/{id}/calendario.ics", produces = "text/calendar")
    @PreAuthorize("hasAnyAuthority('agenda:ler', 'agenda-propria:gerir', 'agendamento-proprio:gerir')")
    ResponseEntity<String> calendario(@PathVariable UUID id) {
        AgendamentoDto a = detalhe(id);
        String ics = String.join("\r\n", "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Clinica//Agenda//PT-BR",
                "BEGIN:VEVENT", "UID:" + a.id() + "@clinica", "DTSTAMP:" + ICS.format(Instant.now()),
                "DTSTART:" + ICS.format(a.inicio()), "DTEND:" + ICS.format(a.fim()),
                "SUMMARY:" + textoIcs(a.servico() + " com " + a.profissional()), "END:VEVENT", "END:VCALENDAR", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"consulta.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(ics);
    }

    private static String textoIcs(String texto) {
        return texto.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replaceAll("[\\r\\n]", " ");
    }

    /** @param pacienteId obrigatório para a recepção; o paciente agenda sempre para si */
    record NovoAgendamento(@NotNull UUID servicoId, @NotNull UUID profissionalId, @NotNull Instant inicio,
                           UUID pacienteId, @Size(max = 500) String observacao) { }

    record Remarcacao(@NotNull Instant inicio, long versao) { }

    record Cancelamento(@Size(max = 200) String motivo) { }
}
