package br.com.clinica.identidadeacesso.infrastructure;

import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Usuário do modo SIMPLES com nome de exibição. */
final class UsuarioDemo extends User implements CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final String nomeExibicao;

    UsuarioDemo(String usuario, String senha, String nomeExibicao, Collection<? extends GrantedAuthority> autoridades) {
        super(usuario, senha, autoridades);
        this.nomeExibicao = nomeExibicao;
    }

    String nomeExibicao() {
        return nomeExibicao;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof UsuarioDemo u && super.equals(u) && Objects.equals(nomeExibicao, u.nomeExibicao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), nomeExibicao);
    }
}
