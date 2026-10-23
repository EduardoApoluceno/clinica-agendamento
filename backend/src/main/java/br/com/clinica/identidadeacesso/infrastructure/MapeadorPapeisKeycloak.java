package br.com.clinica.identidadeacesso.infrastructure;

import br.com.clinica.identidadeacesso.domain.Papel;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Converte o claim {@code roles} do ID token (mapper de realm roles configurado no Keycloak) em
 * autoridades {@code ROLE_*}. Papéis desconhecidos são ignorados.
 */
class MapeadorPapeisKeycloak implements GrantedAuthoritiesMapper {

    static final String CLAIM_PAPEIS = "roles";

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> origem) {
        Set<GrantedAuthority> resultado = new HashSet<>(origem);
        origem.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(OidcUserAuthority.class::cast)
                .flatMap(a -> papeisDoToken(a).stream())
                .map(Papel::doNome)
                .flatMap(Optional::stream)
                .map(p -> new SimpleGrantedAuthority(p.autoridade()))
                .forEach(resultado::add);
        return resultado;
    }

    private static List<String> papeisDoToken(OidcUserAuthority autoridade) {
        List<String> papeis = autoridade.getIdToken().getClaimAsStringList(CLAIM_PAPEIS);
        return papeis == null ? List.of() : papeis;
    }
}
