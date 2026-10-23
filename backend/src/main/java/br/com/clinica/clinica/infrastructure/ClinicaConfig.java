package br.com.clinica.clinica.infrastructure;

import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.clinica.application.ClinicaRepositorio;
import br.com.clinica.clinica.application.GerirClinica;
import br.com.clinica.compartilhado.application.ContextoClinica;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClinicaConfig {

    @Bean
    GerirClinica gerirClinica(ClinicaRepositorio repositorio, ContextoClinica contexto, RegistradorAuditoria auditoria) {
        return new GerirClinica(repositorio, contexto, auditoria);
    }
}
