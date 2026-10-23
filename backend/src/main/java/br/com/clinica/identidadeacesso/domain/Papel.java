package br.com.clinica.identidadeacesso.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Papéis de acesso (RBAC). As permissões finas derivam destes papéis. */
public enum Papel {
    ADMIN, RECEPCAO, PROFISSIONAL, PACIENTE;

    public static final String PREFIXO_AUTORIDADE = "ROLE_";

    /** Converte o nome vindo do IdP; ignora papéis desconhecidos (ex.: default-roles do Keycloak). */
    public static Optional<Papel> doNome(String nome) {
        String normalizado = nome.toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.name().equals(normalizado)).findFirst();
    }

    public String autoridade() {
        return PREFIXO_AUTORIDADE + name();
    }

    public boolean equipe() {
        return this != PACIENTE;
    }
}
