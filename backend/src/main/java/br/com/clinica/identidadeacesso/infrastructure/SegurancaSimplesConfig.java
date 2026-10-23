package br.com.clinica.identidadeacesso.infrastructure;

import br.com.clinica.compartilhado.domain.Papel;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticação simplificada do perfil demo-leve (sem Keycloak), apenas para visualização em
 * máquinas com pouca memória. Nunca habilitar em produção: o perfil prod recusa este modo.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.modo", havingValue = "SIMPLES")
class SegurancaSimplesConfig {

    SegurancaSimplesConfig(Environment ambiente) {
        if (ambiente.matchesProfiles("prod")) {
            throw new IllegalStateException("app.auth.modo=SIMPLES é proibido no perfil prod");
        }
    }

    @Bean
    SecurityFilterChain cadeiaSimples(HttpSecurity http) {
        PoliticasHttp.aplicar(http);
        http
            .formLogin(form -> form.defaultSuccessUrl("/", true))
            .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager usuariosDemo(AutenticacaoProperties props, PasswordEncoder encoder) {
        var usuarios = props.usuariosDemo().stream()
                .map(u -> new UsuarioDemo(u.usuario(), encoder.encode(u.senha()), u.nome(),
                        AutoridadesDosPapeis.de(Papel.efetivos(u.papeis().stream()
                                .map(Papel::doNome).flatMap(Optional::stream).toList()))))
                .toArray(UserDetails[]::new);
        return new InMemoryUserDetailsManager(usuarios);
    }
}
