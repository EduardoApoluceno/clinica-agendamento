package br.com.clinica.clinica.adapters.in.web;

import br.com.clinica.clinica.adapters.in.web.ClinicaController.EnderecoDto;
import br.com.clinica.clinica.application.GerirClinica;
import br.com.clinica.clinica.domain.Clinica;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Informações públicas (portal e assistente): nome, endereço e telefone. Sem CNPJ nem configurações. */
@RestController
class ClinicaPublicaController {

    private final GerirClinica gerirClinica;

    ClinicaPublicaController(GerirClinica gerirClinica) {
        this.gerirClinica = gerirClinica;
    }

    @GetMapping("/api/v1/publico/clinica")
    ClinicaPublica obter() {
        Clinica c = gerirClinica.obter();
        return new ClinicaPublica(c.nome(), EnderecoDto.de(c.endereco()), c.telefone().orElse(null));
    }

    record ClinicaPublica(String nome, EnderecoDto endereco, String telefone) { }
}
