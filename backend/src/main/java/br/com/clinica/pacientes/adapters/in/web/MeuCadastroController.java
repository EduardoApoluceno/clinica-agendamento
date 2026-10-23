package br.com.clinica.pacientes.adapters.in.web;

import br.com.clinica.compartilhado.domain.Email;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.compartilhado.domain.Telefone;
import br.com.clinica.pacientes.adapters.in.web.PacienteDtos.ConsentimentoDto;
import br.com.clinica.pacientes.adapters.in.web.PacienteDtos.DadosPessoaisDto;
import br.com.clinica.pacientes.adapters.in.web.PacienteDtos.PacienteResposta;
import br.com.clinica.pacientes.application.CadastroProprio;
import br.com.clinica.pacientes.application.CadastroProprio.ExportacaoDados;
import br.com.clinica.pacientes.application.GerirConsentimentos;
import br.com.clinica.pacientes.application.VerificarTelefone;
import br.com.clinica.pacientes.application.VerificarTelefone.CodigoEnviado;
import br.com.clinica.pacientes.domain.Consentimento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Autoatendimento do paciente: o titular é sempre a conta da sessão. */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasAuthority('cadastro-proprio:gerir')")
class MeuCadastroController {

    private final CadastroProprio cadastro;
    private final VerificarTelefone verificacao;
    private final GerirConsentimentos consentimentos;

    MeuCadastroController(CadastroProprio cadastro, VerificarTelefone verificacao,
                          GerirConsentimentos consentimentos) {
        this.cadastro = cadastro;
        this.verificacao = verificacao;
        this.consentimentos = consentimentos;
    }

    @GetMapping("/paciente")
    PacienteResposta meu() {
        return cadastro.meu().map(p -> PacienteResposta.de(p, true))
                .orElseThrow(() -> new RecursoNaoEncontradoException("paciente"));
    }

    @PostMapping("/paciente")
    @ResponseStatus(HttpStatus.CREATED)
    PacienteResposta completar(@Valid @RequestBody CompletarRequisicao req, Authentication auth) {
        return PacienteResposta.de(cadastro.completar(req.dados().paraDominio(), Telefone.de(req.celular()),
                emailVerificado(auth)), true);
    }

    @PutMapping("/paciente")
    PacienteResposta atualizar(@Valid @RequestBody AtualizarRequisicao req) {
        return PacienteResposta.de(cadastro.atualizar(req.dados().paraDominio(), req.versao()), true);
    }

    @PostMapping("/telefone/codigo")
    CodigoEnviado solicitarCodigo(@Valid @RequestBody SolicitarCodigoRequisicao req) {
        return verificacao.solicitar(Telefone.de(req.celular()));
    }

    @PostMapping("/telefone/verificacao")
    PacienteResposta confirmarCodigo(@Valid @RequestBody ConfirmarCodigoRequisicao req) {
        return PacienteResposta.de(verificacao.confirmar(req.codigo()), true);
    }

    @GetMapping("/consentimentos")
    List<ConsentimentoDto> consentimentos() {
        return consentimentos.meus().stream().map(ConsentimentoDto::de).toList();
    }

    @PostMapping("/consentimentos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void conceder(@Valid @RequestBody ConcederRequisicao req) {
        consentimentos.conceder(req.finalidade(), req.versaoTermo());
    }

    @DeleteMapping("/consentimentos/{finalidade}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revogar(@PathVariable Consentimento.Finalidade finalidade) {
        consentimentos.revogar(finalidade);
    }

    @GetMapping("/exportacao-dados")
    ResponseEntity<ExportacaoResposta> exportar() {
        ExportacaoDados dados = cadastro.exportar();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados.json\"")
                .body(new ExportacaoResposta(PacienteResposta.de(dados.paciente(), true),
                        dados.consentimentos().stream().map(ConsentimentoDto::de).toList(), dados.geradoEm()));
    }

    /** E-mail só é aproveitado quando o Keycloak confirma que foi verificado. */
    private static Optional<Email> emailVerificado(Authentication auth) {
        return Optional.of(auth.getPrincipal())
                .filter(OidcUser.class::isInstance).map(OidcUser.class::cast)
                .filter(u -> Boolean.TRUE.equals(u.getEmailVerified()))
                .map(OidcUser::getEmail)
                .map(Email::new);
    }

    record CompletarRequisicao(@NotNull @Valid DadosPessoaisDto dados, @NotBlank @Size(max = 20) String celular) { }

    record AtualizarRequisicao(@NotNull @Valid DadosPessoaisDto dados, @NotNull Long versao) { }

    record SolicitarCodigoRequisicao(@NotBlank @Size(max = 20) String celular) { }

    record ConfirmarCodigoRequisicao(@NotBlank @Pattern(regexp = "\\d{6}") String codigo) { }

    record ConcederRequisicao(@NotNull Consentimento.Finalidade finalidade,
                              @NotBlank @Size(max = 20) String versaoTermo) { }

    record ExportacaoResposta(PacienteResposta cadastro, List<ConsentimentoDto> consentimentos, String geradoEm) { }
}
