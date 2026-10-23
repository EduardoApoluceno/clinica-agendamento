package br.com.clinica.identidadeacesso.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class MapeadorPapeisKeycloakTest {

    @Test
    void converteClaimRolesEmAutoridades() {
        OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "u1", "roles", List.of("recepcao", "default-roles-clinica")));

        var autoridades = new MapeadorPapeisKeycloak().mapAuthorities(List.of(new OidcUserAuthority(token)));

        assertThat(autoridades).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_RECEPCAO")
                .doesNotContain("ROLE_DEFAULT-ROLES-CLINICA");
    }

    @Test
    void registroUsaUrlPublicaNoNavegadorEInternaNoBackchannel() {
        var oidc = new AutenticacaoProperties.Oidc("clinica-bff", "s",
                "http://localhost:8180/realms/clinica", "http://keycloak:8180/realms/clinica");

        var registro = SegurancaOidcConfig.registroKeycloak(oidc);

        assertThat(registro.getProviderDetails().getAuthorizationUri()).startsWith("http://localhost:8180/");
        assertThat(registro.getProviderDetails().getTokenUri()).startsWith("http://keycloak:8180/");
        assertThat(registro.getProviderDetails().getIssuerUri()).isEqualTo("http://localhost:8180/realms/clinica");
        assertThat(registro.getClientSettings().isRequireProofKey()).isTrue();
    }
}
