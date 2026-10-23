package br.com.clinica.identidadeacesso.infrastructure;

import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Políticas HTTP comuns a todos os modos de autenticação. */
final class PoliticasHttp {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; connect-src 'self'; font-src 'self'; object-src 'none'; "
            + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    private static final long UM_ANO_SEGUNDOS = 31_536_000L;

    private PoliticasHttp() {
    }

    static void aplicar(HttpSecurity http) {
        http
            .csrf(csrf -> csrf.spa())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)
                        .maxAgeInSeconds(UM_ANO_SEGUNDOS)))
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/publico/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated());
    }
}
