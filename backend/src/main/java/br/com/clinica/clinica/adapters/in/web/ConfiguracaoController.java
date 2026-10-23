package br.com.clinica.clinica.adapters.in.web;

import br.com.clinica.clinica.application.ClinicaRepositorio.ConfiguracaoComVersao;
import br.com.clinica.clinica.application.GerirClinica;
import br.com.clinica.clinica.domain.ConfiguracaoAgenda;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Regras configuráveis da agenda, expostas em minutos/dias para facilitar o formulário. */
@RestController
@RequestMapping("/api/v1/clinica/configuracoes")
class ConfiguracaoController {

    private final GerirClinica gerirClinica;

    ConfiguracaoController(GerirClinica gerirClinica) {
        this.gerirClinica = gerirClinica;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('clinica:ler')")
    ConfiguracaoDto obter() {
        return ConfiguracaoDto.de(gerirClinica.configuracao());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('clinica:administrar')")
    ConfiguracaoDto atualizar(@Valid @RequestBody ConfiguracaoDto dto) {
        return ConfiguracaoDto.de(gerirClinica.atualizarConfiguracao(dto.paraDominio(), dto.versao()));
    }

    record ConfiguracaoDto(
            @NotNull @Min(0) @Max(10_080) Integer antecedenciaMinimaCancelamentoMinutos,
            @NotNull @Min(0) @Max(10_080) Integer antecedenciaMinimaAgendamentoMinutos,
            @NotNull @Min(1) @Max(365) Integer horizonteAgendamentoDias,
            @NotNull @Min(5) @Max(60) Integer minutosHoldPagamento,
            @NotNull @Size(max = 5) List<@NotNull @Min(1) @Max(10_080) Integer> lembretesMinutos,
            @NotNull @Min(5) @Max(1_440) Integer prazoAceiteListaEsperaMinutos,
            @NotNull Boolean smsFallbackAtivo,
            @NotNull Boolean exigeJustificativaProntuarioSemVinculo,
            @NotNull Long versao) {

        static ConfiguracaoDto de(ConfiguracaoComVersao cv) {
            ConfiguracaoAgenda c = cv.configuracao();
            return new ConfiguracaoDto((int) c.antecedenciaMinimaCancelamento().toMinutes(),
                    (int) c.antecedenciaMinimaAgendamento().toMinutes(), (int) c.horizonteAgendamento().toDays(),
                    c.minutosHoldPagamento(), c.lembretes().stream().map(d -> (int) d.toMinutes()).toList(),
                    (int) c.prazoAceiteListaEspera().toMinutes(), c.smsFallbackAtivo(),
                    c.exigeJustificativaProntuarioSemVinculo(), cv.versao());
        }

        ConfiguracaoAgenda paraDominio() {
            return new ConfiguracaoAgenda(Duration.ofMinutes(antecedenciaMinimaCancelamentoMinutos),
                    Duration.ofMinutes(antecedenciaMinimaAgendamentoMinutos), Duration.ofDays(horizonteAgendamentoDias),
                    minutosHoldPagamento, lembretesMinutos.stream().map(Duration::ofMinutes).toList(),
                    Duration.ofMinutes(prazoAceiteListaEsperaMinutos), smsFallbackAtivo,
                    exigeJustificativaProntuarioSemVinculo);
        }
    }
}
