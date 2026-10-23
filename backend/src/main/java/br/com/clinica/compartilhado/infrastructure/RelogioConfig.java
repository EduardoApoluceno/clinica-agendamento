package br.com.clinica.compartilhado.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio único (UTC) injetável — permite testes determinísticos de regras com tempo. */
@Configuration(proxyBeanMethods = false)
class RelogioConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
