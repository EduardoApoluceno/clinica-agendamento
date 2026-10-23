package br.com.clinica.pacientes.adapters.in.web;

import br.com.clinica.compartilhado.application.Pagina;
import br.com.clinica.compartilhado.domain.Permissao;
import br.com.clinica.pacientes.adapters.in.web.PacienteDtos.ConsentimentoDto;
import br.com.clinica.pacientes.adapters.in.web.PacienteDtos.ContatoDto;
import br.com.clinica.pacientes.adapters.in.web.PacienteDtos.DadosPessoaisDto;
import br.com.clinica.pacientes.adapters.in.web.PacienteDtos.PacienteResposta;
import br.com.clinica.pacientes.application.GerirConsentimentos;
import br.com.clinica.pacientes.application.GerirPacientes;
import br.com.clinica.pacientes.application.PacientesPortas.ResumoPaciente;
import br.com.clinica.pacientes.domain.Contato;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pacientes para a equipe. Quem só lê (profissional) recebe CPF e contatos mascarados. */
@RestController
@RequestMapping("/api/v1/pacientes")
class PacientesController {

    private static final int TAMANHO_PADRAO = 20;

    private final GerirPacientes gerir;
    private final GerirConsentimentos consentimentos;

    PacientesController(GerirPacientes gerir, GerirConsentimentos consentimentos) {
        this.gerir = gerir;
        this.consentimentos = consentimentos;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pacientes:ler')")
    Pagina<ResumoPaciente> pesquisar(@RequestParam String termo, @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "" + TAMANHO_PADRAO) int tamanho) {
        return gerir.pesquisar(termo, new Pagina.Pedido(pagina, tamanho));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pacientes:ler')")
    PacienteResposta obter(@PathVariable UUID id, Authentication auth) {
        return PacienteResposta.de(gerir.obter(id), podeVerCompleto(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pacientes:escrever')")
    PacienteResposta cadastrar(@Valid @RequestBody PacienteRequisicao req) {
        return PacienteResposta.de(gerir.cadastrar(req.dados().paraDominio(), req.contatosDominio()), true);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pacientes:escrever')")
    PacienteResposta atualizar(@PathVariable UUID id, @Valid @RequestBody PacienteRequisicao req) {
        long versao = req.versao() == null ? -1 : req.versao();
        return PacienteResposta.de(gerir.atualizar(id, req.dados().paraDominio(), req.contatosDominio(), versao), true);
    }

    @GetMapping("/{id}/consentimentos")
    @PreAuthorize("hasAuthority('pacientes:ler')")
    List<ConsentimentoDto> consentimentos(@PathVariable UUID id) {
        return consentimentos.doPaciente(id).stream().map(ConsentimentoDto::de).toList();
    }

    private static boolean podeVerCompleto(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> Permissao.PACIENTES_ESCREVER.autoridade().equals(a.getAuthority()));
    }

    record PacienteRequisicao(@NotNull @Valid DadosPessoaisDto dados, @Size(max = 6) List<@Valid ContatoDto> contatos,
                              Long versao) {

        List<Contato> contatosDominio() {
            return contatos == null ? List.of() : contatos.stream().map(ContatoDto::paraDominio).toList();
        }
    }
}
