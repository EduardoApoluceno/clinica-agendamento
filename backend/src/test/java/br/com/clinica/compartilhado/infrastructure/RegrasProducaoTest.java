package br.com.clinica.compartilhado.infrastructure;

import br.com.clinica.compartilhado.infrastructure.criptografia.CriptografiaProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasProducaoTest {

    private static final String K1 = "CcsE6v4KtIYoMn91+BaiPAoaMPVc+DCpXwdu/qP4X58=";
    private static final String HMAC = "Mv/gS4HkTkyVmobp+OEvobuiqozmA02knQ844Srk/74=";
    private static final String K1_DEMO = "23zDWKrsjR+k4rhJxTnGS97I1mA80NWbWYQveTp5q2U=";

    private final Map<String, String> props = new HashMap<>(Map.of(
            "spring.datasource.password", "s3nh4-forte-app",
            "spring.flyway.password", "s3nh4-forte-migracao",
            "spring.data.redis.password", "s3nh4-forte-redis",
            "app.auth.oidc.client-secret", "s3gr3do-forte",
            "app.auth.fluxo-navegador", "navegador-clinica"));

    private CriptografiaProperties cripto = new CriptografiaProperties(Map.of("k1", K1), "k1", HMAC);

    @Test
    void aceitaConfiguracaoForte() {
        assertThat(RegrasProducao.problemas(props::get, cripto)).isEmpty();
    }

    @Test
    void recusaSegredosDeDemonstracaoOuVazios() {
        props.put("spring.datasource.password", "demo-app");
        props.put("app.auth.oidc.client-secret", "demo-segredo-bff-troque-em-producao");
        props.remove("spring.data.redis.password");

        assertThat(RegrasProducao.problemas(props::get, cripto)).containsExactlyInAnyOrder(
                "spring.datasource.password com o valor de demonstração",
                "app.auth.oidc.client-secret com o valor de demonstração",
                "spring.data.redis.password vazio");
    }

    @Test
    void flywaySemUsuarioProprioUsaODatasource() {
        props.remove("spring.flyway.password");
        assertThat(RegrasProducao.problemas(props::get, cripto)).isEmpty();
    }

    @Test
    void recusaChavesDeDemonstracaoOuAusentes() {
        cripto = new CriptografiaProperties(Map.of("k1", K1_DEMO), "k1", "");
        assertThat(RegrasProducao.problemas(props::get, cripto)).containsExactlyInAnyOrder(
                "app.criptografia.chaves.k1 com a chave de demonstração",
                "app.criptografia.chave-hmac vazio");

        cripto = new CriptografiaProperties(Map.of(), "k1", HMAC);
        assertThat(RegrasProducao.problemas(props::get, cripto)).containsExactly("app.criptografia.chaves vazio");
    }

    @Test
    void recusaFluxoSemSegundoFator() {
        props.put("app.auth.fluxo-navegador", "browser");
        assertThat(RegrasProducao.problemas(props::get, cripto))
                .containsExactly("KC_FLUXO_NAVEGADOR=browser desliga o MFA da equipe");
    }
}
