package br.com.clinica.agenda.adapters.in.web;

import br.com.clinica.agenda.application.ConsultarAgenda;
import br.com.clinica.agenda.application.ConsultarAgenda.AgendaDoDia;
import br.com.clinica.agenda.application.ConsultarAgenda.Coluna;
import br.com.clinica.agenda.application.ConsultarAgenda.Resumo;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Grade do dia da recepção: uma coluna por profissional (o profissional vê só a dele). */
@RestController
class AgendaDiaController {

    private final ConsultarAgenda consultar;

    AgendaDiaController(ConsultarAgenda consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/api/v1/agenda/dia")
    @PreAuthorize("hasAnyAuthority('agenda:ler', 'agenda-propria:gerir')")
    AgendaDiaDto dia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return AgendaDiaDto.de(consultar.dia(data));
    }

    record AgendaDiaDto(LocalDate data, List<ColunaDto> colunas, Resumo resumo) {
        static AgendaDiaDto de(AgendaDoDia d) {
            return new AgendaDiaDto(d.data(), d.colunas().stream().map(ColunaDto::de).toList(), d.resumo());
        }
    }

    record ColunaDto(UUID profissionalId, String profissional, List<IntervaloDto> expediente,
                     List<AusenciaDto> ausencias, List<AgendamentoDto> agendamentos, List<IntervaloDto> livres) {
        static ColunaDto de(Coluna c) {
            return new ColunaDto(c.profissionalId(), c.profissional(),
                    c.expediente().stream().map(IntervaloDto::de).toList(),
                    c.ausencias().stream().map(a -> new AusenciaDto(a.periodo().inicio(), a.periodo().fim(), a.tipo()))
                            .toList(),
                    c.agendamentos().stream().map(AgendamentoDto::de).toList(),
                    c.livres().stream().map(IntervaloDto::de).toList());
        }
    }

    record IntervaloDto(Instant inicio, Instant fim) {
        static IntervaloDto de(Periodo p) {
            return new IntervaloDto(p.inicio(), p.fim());
        }
    }

    record AusenciaDto(Instant inicio, Instant fim, String tipo) { }
}
