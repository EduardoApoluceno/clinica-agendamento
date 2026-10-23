package br.com.clinica.identidadeacesso.infrastructure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de autenticação.
 *
 * @param modo OIDC (Keycloak, padrão) ou SIMPLES (apenas perfil demo-leve, para visualização)
 * @param oidc dados do cliente confidencial no Keycloak
 * @param usuariosDemo usuários em memória do modo SIMPLES
 */
@ConfigurationProperties("app.auth")
public record AutenticacaoProperties(Modo modo, Oidc oidc, List<UsuarioDemo> usuariosDemo) {

    public enum Modo { OIDC, SIMPLES }

    public AutenticacaoProperties {
        modo = modo == null ? Modo.OIDC : modo;
        usuariosDemo = usuariosDemo == null ? List.of() : List.copyOf(usuariosDemo);
    }

    /**
     * @param issuerPublico URL do realm vista pelo navegador (também o "iss" dos tokens)
     * @param issuerInterno URL do realm vista pelo backend (rede Docker); igual à pública em prod
     */
    public record Oidc(String clientId, String clientSecret, String issuerPublico, String issuerInterno) { }

    public record UsuarioDemo(String usuario, String senha, String nome, List<String> papeis) { }
}
