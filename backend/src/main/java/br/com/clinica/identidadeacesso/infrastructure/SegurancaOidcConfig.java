package br.com.clinica.identidadeacesso.infrastructure;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Padrão BFF (ADR-0002): o backend é cliente OAuth2 confidencial do Keycloak (Authorization Code +
 * PKCE). Os tokens ficam na sessão do servidor; o navegador recebe somente o cookie de sessão.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.modo", havingValue = "OIDC", matchIfMissing = true)
class SegurancaOidcConfig {

    static final String REGISTRO = "keycloak";
    private static final String OIDC_PATH = "/protocol/openid-connect";

    @Bean
    SecurityFilterChain cadeiaOidc(HttpSecurity http, ClientRegistrationRepository registros) {
        PoliticasHttp.aplicar(http);
        http
            .oauth2Login(login -> login.defaultSuccessUrl("/", true))
            .logout(logout -> logout.logoutSuccessHandler(logoutGlobal(registros)));
        return http.build();
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(AutenticacaoProperties props) {
        return new InMemoryClientRegistrationRepository(registroKeycloak(props.oidc()));
    }

    @Bean
    GrantedAuthoritiesMapper mapeadorPapeis() {
        return new MapeadorPapeisKeycloak();
    }

    /**
     * Endpoints de navegador usam a URL pública; chamadas servidor-a-servidor usam a URL interna.
     * O "iss" esperado é sempre o público (Keycloak com hostname fixo e backchannel dinâmico).
     */
    static ClientRegistration registroKeycloak(AutenticacaoProperties.Oidc oidc) {
        String publico = oidc.issuerPublico();
        String interno = oidc.issuerInterno();
        return ClientRegistration.withRegistrationId(REGISTRO)
                .clientName("Keycloak")
                .clientId(oidc.clientId())
                .clientSecret(oidc.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(publico)
                .authorizationUri(publico + OIDC_PATH + "/auth")
                .tokenUri(interno + OIDC_PATH + "/token")
                .jwkSetUri(interno + OIDC_PATH + "/certs")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", publico + OIDC_PATH + "/logout"))
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .build();
    }

    private static OidcClientInitiatedLogoutSuccessHandler logoutGlobal(ClientRegistrationRepository registros) {
        var handler = new OidcClientInitiatedLogoutSuccessHandler(registros);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
