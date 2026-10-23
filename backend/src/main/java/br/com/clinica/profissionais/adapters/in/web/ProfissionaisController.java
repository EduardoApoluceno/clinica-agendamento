package br.com.clinica.profissionais.adapters.in.web;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.profissionais.application.GerirProfissionais;
import br.com.clinica.profissionais.domain.Profissional;
import br.com.clinica.profissionais.domain.Profissional.DadosProfissional;
import br.com.clinica.profissionais.domain.RegistroConselho;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProfissionaisController {

    private final GerirProfissionais gerir;

    ProfissionaisController(GerirProfissionais gerir) {
        this.gerir = gerir;
    }

    @GetMapping("/api/v1/profissionais")
    @PreAuthorize("hasAuthority('profissionais:ler')")
    List<ProfissionalResposta> listar(@RequestParam(defaultValue = "false") boolean incluirInativos) {
        return gerir.listar(!incluirInativos).stream().map(ProfissionalResposta::de).toList();
    }

    @GetMapping("/api/v1/profissionais/{id}")
    @PreAuthorize("hasAuthority('profissionais:ler')")
    ProfissionalResposta obter(@PathVariable UUID id) {
        return ProfissionalResposta.de(gerir.obter(id));
    }

    /** Cadastro do profissional vinculado à conta logada ("minha agenda"). */
    @GetMapping("/api/v1/me/profissional")
    @PreAuthorize("hasAuthority('agenda-propria:gerir')")
    ProfissionalResposta meu() {
        return ProfissionalResposta.de(gerir.doUsuarioAtual());
    }

    @PostMapping("/api/v1/profissionais")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('profissionais:administrar')")
    ProfissionalResposta cadastrar(@Valid @RequestBody ProfissionalRequisicao req) {
        return ProfissionalResposta.de(gerir.cadastrar(req.paraDados()));
    }

    @PutMapping("/api/v1/profissionais/{id}")
    @PreAuthorize("hasAuthority('profissionais:administrar')")
    ProfissionalResposta atualizar(@PathVariable UUID id, @Valid @RequestBody ProfissionalRequisicao req) {
        return ProfissionalResposta.de(gerir.atualizar(id, req.paraDados(), exigirVersao(req.versao())));
    }

    @PatchMapping("/api/v1/profissionais/{id}/ativo")
    @PreAuthorize("hasAuthority('profissionais:administrar')")
    ProfissionalResposta alterarAtivo(@PathVariable UUID id, @Valid @RequestBody AtivoRequisicao req) {
        return ProfissionalResposta.de(gerir.alterarAtivo(id, req.ativo(), req.versao()));
    }

    private static long exigirVersao(Long versao) {
        if (versao == null) {
            throw new DadoInvalidoException("versao-obrigatoria", "Informe a versão do registro.");
        }
        return versao;
    }

    record ProfissionalRequisicao(@NotBlank @Size(max = 160) String nome, @NotBlank String conselho,
                                  @NotBlank @Size(max = 20) String numeroConselho, @NotBlank String ufConselho,
                                  @Size(max = 64) String usuarioSub, Set<UUID> especialidades, Set<UUID> servicos,
                                  Long versao) {

        DadosProfissional paraDados() {
            return new DadosProfissional(nome, new RegistroConselho(conselho, numeroConselho, ufConselho),
                    Optional.ofNullable(usuarioSub), especialidades == null ? Set.of() : especialidades,
                    servicos == null ? Set.of() : servicos);
        }
    }

    record AtivoRequisicao(@NotNull Boolean ativo, @NotNull Long versao) { }

    record ProfissionalResposta(UUID id, String nome, String conselho, String numeroConselho, String ufConselho,
                                String usuarioSub, Set<UUID> especialidades, Set<UUID> servicos, boolean ativo,
                                long versao) {

        static ProfissionalResposta de(Profissional p) {
            return new ProfissionalResposta(p.id(), p.nome(), p.registro().conselho(), p.registro().numero(),
                    p.registro().uf(), p.usuarioSub().orElse(null), p.especialidades(), p.servicos(), p.ativo(),
                    p.versao());
        }
    }
}
