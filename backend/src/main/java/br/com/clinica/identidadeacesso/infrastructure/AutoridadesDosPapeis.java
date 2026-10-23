package br.com.clinica.identidadeacesso.infrastructure;

import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.Permissao;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Papéis → autoridades Spring: {@code ROLE_<PAPEL>} + uma autoridade por permissão fina. */
final class AutoridadesDosPapeis {

    private AutoridadesDosPapeis() {
    }

    static Set<GrantedAuthority> de(Collection<Papel> papeis) {
        return papeis.stream()
                .flatMap(p -> Stream.concat(Stream.of(p.autoridade()),
                        p.permissoes().stream().map(Permissao::autoridade)))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}
