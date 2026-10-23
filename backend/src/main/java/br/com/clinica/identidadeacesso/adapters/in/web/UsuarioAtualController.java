package br.com.clinica.identidadeacesso.adapters.in.web;

import br.com.clinica.identidadeacesso.domain.Papel;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do usuário da sessão atual, usados pelo frontend para montar menu e permissões. */
@RestController
@RequestMapping("/api/v1/me")
class UsuarioAtualController {

    private final boolean ambienteDemo;

    UsuarioAtualController(@Value("${app.demo.ativo:false}") boolean ambienteDemo) {
        this.ambienteDemo = ambienteDemo;
    }

    @GetMapping
    UsuarioAtualResposta atual(Authentication autenticacao) {
        Optional<OidcUser> oidc = Optional.of(autenticacao.getPrincipal())
                .filter(OidcUser.class::isInstance)
                .map(OidcUser.class::cast);
        return new UsuarioAtualResposta(
                oidc.map(OidcUser::getSubject).orElse(autenticacao.getName()),
                oidc.map(OidcUser::getFullName).orElse(autenticacao.getName()),
                oidc.map(OidcUser::getEmail),
                papeis(autenticacao),
                ambienteDemo);
    }

    private static Set<String> papeis(Authentication autenticacao) {
        Set<String> papeis = new TreeSet<>();
        autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith(Papel.PREFIXO_AUTORIDADE))
                .map(a -> a.substring(Papel.PREFIXO_AUTORIDADE.length()))
                .forEach(papeis::add);
        return papeis;
    }

    record UsuarioAtualResposta(String id, String nome, Optional<String> email, Set<String> papeis,
                                boolean ambienteDemo) { }
}
