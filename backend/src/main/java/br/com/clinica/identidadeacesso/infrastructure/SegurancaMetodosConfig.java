package br.com.clinica.identidadeacesso.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Habilita @PreAuthorize com permissões finas (ex.: {@code hasAuthority('pacientes:ler')}). */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SegurancaMetodosConfig {
}
