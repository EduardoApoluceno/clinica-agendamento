package br.com.clinica.clinica.adapters.in.web;

import br.com.clinica.clinica.application.GerirClinica;
import br.com.clinica.clinica.application.GerirClinica.DadosClinica;
import br.com.clinica.clinica.domain.Clinica;
import br.com.clinica.clinica.domain.Cnpj;
import br.com.clinica.clinica.domain.Endereco;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinica")
class ClinicaController {

    private final GerirClinica gerirClinica;

    ClinicaController(GerirClinica gerirClinica) {
        this.gerirClinica = gerirClinica;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('clinica:ler')")
    ClinicaResposta obter() {
        return ClinicaResposta.de(gerirClinica.obter());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('clinica:administrar')")
    ClinicaResposta atualizar(@Valid @RequestBody ClinicaRequisicao req) {
        return ClinicaResposta.de(gerirClinica.atualizarDados(req.paraComando()));
    }

    record EnderecoDto(@NotBlank @Size(max = 160) String logradouro, @NotBlank @Size(max = 20) String numero,
                       @Size(max = 80) String complemento, @NotBlank @Size(max = 80) String bairro,
                       @NotBlank @Size(max = 80) String cidade, @NotBlank @Pattern(regexp = "[A-Z]{2}") String uf,
                       @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}") String cep) {

        static EnderecoDto de(Endereco e) {
            return new EnderecoDto(e.logradouro(), e.numero(), e.complemento().orElse(null), e.bairro(), e.cidade(),
                    e.uf(), e.cep());
        }

        Endereco paraDominio() {
            return new Endereco(logradouro, numero, Optional.ofNullable(complemento), bairro, cidade, uf, cep);
        }
    }

    record ClinicaRequisicao(@NotBlank @Size(max = 160) String nome, @NotBlank String cnpj,
                             @NotNull @Valid EnderecoDto endereco, @Size(max = 20) String telefone,
                             @NotNull Long versao) {

        DadosClinica paraComando() {
            return new DadosClinica(nome, Cnpj.de(cnpj), endereco.paraDominio(), Optional.ofNullable(telefone), versao);
        }
    }

    record ClinicaResposta(String id, String nome, String cnpj, String fusoHorario, EnderecoDto endereco,
                           String telefone, long versao) {

        static ClinicaResposta de(Clinica c) {
            return new ClinicaResposta(c.id().toString(), c.nome(), c.cnpj().digitos(), c.fusoHorario().getId(),
                    EnderecoDto.de(c.endereco()), c.telefone().orElse(null), c.versao());
        }
    }
}
