package br.com.clinica.agenda.adapters.in.web;

import br.com.clinica.agenda.application.ConsultarDisponibilidade;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.Horario;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Horários livres (público: o portal mostra antes do login) e avisos de mudança em tempo real. */
@RestController
@RequestMapping("/api/v1/publico/disponibilidade")
class DisponibilidadeController {

    private static final int DIAS_MAXIMOS = 31;

    private final ConsultarDisponibilidade consultar;
    private final AvisosDisponibilidade avisos;

    DisponibilidadeController(ConsultarDisponibilidade consultar, AvisosDisponibilidade avisos) {
        this.consultar = consultar;
        this.avisos = avisos;
    }

    @GetMapping
    List<HorarioDto> horarios(@RequestParam UUID servicoId, @RequestParam UUID profissionalId,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        if (ate.isBefore(de) || de.plusDays(DIAS_MAXIMOS).isBefore(ate)) {
            throw new DadoInvalidoException("intervalo-invalido", "Consulte no máximo 31 dias por vez.");
        }
        return consultar.horarios(servicoId, profissionalId, de, ate).stream().map(HorarioDto::de).toList();
    }

    /** Evento "agenda-alterada" com o id do profissional; sem {@code profissionalId}, de todos. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter avisos(@RequestParam Optional<UUID> profissionalId) {
        return avisos.assinar(profissionalId);
    }

    record HorarioDto(Instant inicio, Instant fim, boolean livre) {
        static HorarioDto de(Horario h) {
            return new HorarioDto(h.periodo().inicio(), h.periodo().fim(), h.livre());
        }
    }
}
