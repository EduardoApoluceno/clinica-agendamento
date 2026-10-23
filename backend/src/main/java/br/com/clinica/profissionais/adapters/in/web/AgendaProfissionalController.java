package br.com.clinica.profissionais.adapters.in.web;

import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.profissionais.application.GerirAgendaProfissional;
import br.com.clinica.profissionais.domain.Ausencia;
import br.com.clinica.profissionais.domain.Expediente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Expediente e ausências de um profissional. A checagem "é o próprio?" fica no caso de uso. */
@RestController
@RequestMapping("/api/v1/profissionais/{id}")
class AgendaProfissionalController {

    private final GerirAgendaProfissional gerir;

    AgendaProfissionalController(GerirAgendaProfissional gerir) {
        this.gerir = gerir;
    }

    @GetMapping("/expediente")
    @PreAuthorize("hasAuthority('profissionais:ler')")
    List<FaixaDto> expediente(@PathVariable UUID id) {
        return gerir.expediente(id).faixas().stream().map(FaixaDto::de).toList();
    }

    @PutMapping("/expediente")
    @PreAuthorize("hasAnyAuthority('profissionais:administrar', 'agenda-propria:gerir')")
    List<FaixaDto> definirExpediente(@PathVariable UUID id, @RequestBody @Size(max = 28) List<@Valid FaixaDto> faixas) {
        Expediente expediente = new Expediente(faixas.stream().map(FaixaDto::paraDominio).toList());
        return gerir.definirExpediente(id, expediente).faixas().stream().map(FaixaDto::de).toList();
    }

    @GetMapping("/ausencias")
    @PreAuthorize("hasAuthority('profissionais:ler')")
    List<AusenciaDto> ausencias(@PathVariable UUID id,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate) {
        return gerir.ausencias(id, new Periodo(de, ate)).stream().map(AusenciaDto::de).toList();
    }

    @PostMapping("/ausencias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ausencias:gerir', 'agenda-propria:gerir')")
    AusenciaDto registrarAusencia(@PathVariable UUID id, @Valid @RequestBody AusenciaRequisicao req) {
        return AusenciaDto.de(gerir.registrarAusencia(id, req.tipo(), new Periodo(req.inicio(), req.fim()),
                Optional.ofNullable(req.motivo())));
    }

    @DeleteMapping("/ausencias/{ausenciaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ausencias:gerir', 'agenda-propria:gerir')")
    void removerAusencia(@PathVariable UUID id, @PathVariable UUID ausenciaId) {
        gerir.removerAusencia(id, ausenciaId);
    }

    record FaixaDto(@NotNull DayOfWeek dia, @NotNull LocalTime inicio, @NotNull LocalTime fim) {
        static FaixaDto de(Expediente.Faixa f) {
            return new FaixaDto(f.dia(), f.inicio(), f.fim());
        }

        Expediente.Faixa paraDominio() {
            return new Expediente.Faixa(dia, inicio, fim);
        }
    }

    record AusenciaRequisicao(@NotNull Ausencia.Tipo tipo, @NotNull Instant inicio, @NotNull Instant fim,
                              @Size(max = 200) String motivo) { }

    record AusenciaDto(UUID id, Ausencia.Tipo tipo, Instant inicio, Instant fim, String motivo) {
        static AusenciaDto de(Ausencia a) {
            return new AusenciaDto(a.id(), a.tipo(), a.periodo().inicio(), a.periodo().fim(), a.motivo().orElse(null));
        }
    }
}
