package br.com.clinica.identidadeacesso.adapters.in.web;

import br.com.clinica.compartilhado.application.Ator;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.Permissao;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do usuário da sessão atual, usados pelo frontend para montar menu e permissões. */
@RestController
@RequestMapping("/api/v1/me")
class UsuarioAtualController {

    private final ProvedorAtor provedorAtor;
    private final boolean ambienteDemo;

    UsuarioAtualController(ProvedorAtor provedorAtor, @Value("${app.demo.ativo:false}") boolean ambienteDemo) {
        this.provedorAtor = provedorAtor;
        this.ambienteDemo = ambienteDemo;
    }

    @GetMapping
    UsuarioAtualResposta atual(Authentication autenticacao) {
        Ator ator = provedorAtor.atual();
        Optional<String> email = Optional.of(autenticacao.getPrincipal())
                .filter(OidcUser.class::isInstance).map(p -> ((OidcUser) p).getEmail());
        Set<String> papeis = new TreeSet<>();
        Set<String> permissoes = new TreeSet<>();
        ator.papeis().forEach(p -> {
            papeis.add(p.name());
            p.permissoes().stream().map(Permissao::autoridade).forEach(permissoes::add);
        });
        return new UsuarioAtualResposta(ator.sub(), ator.nome(), email, papeis, permissoes, ambienteDemo,
                ator.papeis().stream().anyMatch(Papel::equipe));
    }

    record UsuarioAtualResposta(String id, String nome, Optional<String> email, Set<String> papeis,
                                Set<String> permissoes, boolean ambienteDemo, boolean equipe) { }
}
