package br.com.clinica.identidadeacesso.infrastructure;

import br.com.clinica.compartilhado.application.Ator;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.Papel;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ator a partir da sessão autenticada e da requisição. O IP vem de {@code getRemoteAddr()}, já
 * corrigido pelo processamento de X-Forwarded-For do proxy confiável (forward-headers-strategy).
 * Fora de requisição HTTP (jobs, listeners), o ator é o sistema.
 */
@Component
class ProvedorAtorSeguranca implements ProvedorAtor {

    @Override
    public Ator atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Ator.sistema();
        }
        Optional<OidcUser> oidc = Optional.of(auth.getPrincipal())
                .filter(OidcUser.class::isInstance).map(OidcUser.class::cast);
        Optional<HttpServletRequest> requisicao = requisicaoAtual();
        return new Ator(
                oidc.map(OidcUser::getSubject).orElse(auth.getName()),
                nomeExibicao(auth, oidc),
                papeis(auth),
                requisicao.map(HttpServletRequest::getRemoteAddr),
                requisicao.map(r -> r.getHeader("User-Agent")));
    }

    private static String nomeExibicao(Authentication auth, Optional<OidcUser> oidc) {
        if (auth.getPrincipal() instanceof UsuarioDemo demo) {
            return demo.nomeExibicao();
        }
        return oidc.map(OidcUser::getFullName).filter(n -> !n.isBlank()).orElse(auth.getName());
    }

    static Set<Papel> papeis(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith(Papel.PREFIXO_AUTORIDADE))
                .map(a -> Papel.doNome(a.substring(Papel.PREFIXO_AUTORIDADE.length())))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    private static Optional<HttpServletRequest> requisicaoAtual() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(a -> ((ServletRequestAttributes) a).getRequest());
    }
}
