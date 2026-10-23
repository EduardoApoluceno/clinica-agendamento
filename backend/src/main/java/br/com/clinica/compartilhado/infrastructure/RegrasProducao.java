package br.com.clinica.compartilhado.infrastructure;

import br.com.clinica.compartilhado.infrastructure.criptografia.CriptografiaProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Regras de {@link GuardaProducao}: segredos vazios ou de demonstração e fluxo de login sem MFA. */
final class RegrasProducao {

    /** Padrões de demonstração do compose.yaml e do application.yml; nunca valem em produção. */
    static final Map<String, Set<String>> VALORES_DEMO = Map.of(
            "spring.datasource.password", Set.of("demo-app"),
            "spring.flyway.password", Set.of("demo-migracao"),
            "spring.data.redis.password", Set.of("demo-redis"),
            "app.auth.oidc.client-secret", Set.of("demo-segredo-bff-troque-em-producao"));

    static final Set<String> CHAVES_DEMO = Set.of(
            "23zDWKrsjR+k4rhJxTnGS97I1mA80NWbWYQveTp5q2U=",
            "ifIwRpmxDA4i6yI80V0vDW5Gcf1YtOwBH4hiRo03Rkg=");

    private RegrasProducao() { }

    static List<String> problemas(UnaryOperator<String> propriedade, CriptografiaProperties cripto) {
        var problemas = new ArrayList<String>();
        VALORES_DEMO.forEach((nome, demo) -> {
            String valor = propriedade.apply(nome);
            // O Flyway sem usuário próprio usa a conexão do datasource (já conferida).
            if (nome.equals("spring.flyway.password") && valor == null) {
                return;
            }
            if (valor == null || valor.isBlank()) {
                problemas.add(nome + " vazio");
            } else if (demo.contains(valor)) {
                problemas.add(nome + " com o valor de demonstração");
            }
        });
        if (cripto.chaves().isEmpty()) {
            problemas.add("app.criptografia.chaves vazio");
        }
        cripto.chaves().forEach((kid, chave) -> conferirChave("app.criptografia.chaves." + kid, chave, problemas));
        conferirChave("app.criptografia.chave-hmac", cripto.chaveHmac(), problemas);
        if ("browser".equalsIgnoreCase(propriedade.apply("app.auth.fluxo-navegador"))) {
            problemas.add("KC_FLUXO_NAVEGADOR=browser desliga o MFA da equipe");
        }
        return List.copyOf(problemas);
    }

    private static void conferirChave(String nome, String chave, List<String> problemas) {
        if (chave == null || chave.isBlank()) {
            problemas.add(nome + " vazio");
        } else if (CHAVES_DEMO.contains(chave)) {
            problemas.add(nome + " com a chave de demonstração");
        }
    }
}
