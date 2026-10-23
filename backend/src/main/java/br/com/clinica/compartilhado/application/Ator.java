package br.com.clinica.compartilhado.application;

import br.com.clinica.compartilhado.domain.Papel;
import java.util.Optional;
import java.util.Set;

/**
 * Quem executa um caso de uso, e de onde. Vem sempre da sessão autenticada, nunca de parâmetros.
 *
 * @param sub identificador estável do usuário no IdP (ou "sistema")
 */
public record Ator(String sub, String nome, Set<Papel> papeis, Optional<String> ip, Optional<String> userAgent) {

    public static final String SISTEMA = "sistema";

    public Ator {
        papeis = Set.copyOf(papeis);
    }

    public static Ator sistema() {
        return new Ator(SISTEMA, "Sistema", Set.of(), Optional.empty(), Optional.empty());
    }

    public boolean possui(Papel papel) {
        return papeis.contains(papel);
    }

    /** Papel principal para registro em auditoria. */
    public Optional<Papel> papelPrincipal() {
        return papeis.stream().sorted().findFirst();
    }
}
