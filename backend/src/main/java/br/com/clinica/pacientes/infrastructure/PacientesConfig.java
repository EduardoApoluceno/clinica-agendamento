package br.com.clinica.pacientes.infrastructure;

import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.notificacoes.EnvioMensagens;
import br.com.clinica.pacientes.application.CadastroProprio;
import br.com.clinica.pacientes.application.GerirConsentimentos;
import br.com.clinica.pacientes.application.GerirPacientes;
import br.com.clinica.pacientes.application.PacientesPortas.CodigoVerificacaoRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.ConsentimentoRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.ProtetorCodigos;
import br.com.clinica.pacientes.application.VerificarTelefone;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class PacientesConfig {

    @Bean
    GerirPacientes gerirPacientes(PacienteRepositorio pacientes, ContextoClinica contexto,
                                  RegistradorAuditoria auditoria) {
        return new GerirPacientes(pacientes, contexto, auditoria);
    }

    @Bean
    CadastroProprio cadastroProprio(PacienteRepositorio pacientes, ConsentimentoRepositorio consentimentos,
                                    ContextoClinica contexto, ProvedorAtor provedorAtor, RegistradorAuditoria auditoria,
                                    Clock relogio) {
        return new CadastroProprio(pacientes, consentimentos, contexto, provedorAtor, auditoria, relogio);
    }

    @Bean
    GerirConsentimentos gerirConsentimentos(PacienteRepositorio pacientes, ConsentimentoRepositorio consentimentos,
                                            ContextoClinica contexto, ProvedorAtor provedorAtor,
                                            RegistradorAuditoria auditoria, Clock relogio) {
        return new GerirConsentimentos(pacientes, consentimentos, contexto, provedorAtor, auditoria, relogio);
    }

    /** Expor o código na resposta é permitido só em demonstração; o perfil prod recusa. */
    @Bean
    VerificarTelefone verificarTelefone(PacienteRepositorio pacientes, CodigoVerificacaoRepositorio codigos,
                                        ProtetorCodigos protetor, EnvioMensagens envio, ContextoClinica contexto,
                                        ProvedorAtor provedorAtor, RegistradorAuditoria auditoria, Clock relogio,
                                        @Value("${app.demo.expor-codigos:false}") boolean exporCodigos,
                                        Environment ambiente) {
        if (exporCodigos && ambiente.matchesProfiles("prod")) {
            throw new IllegalStateException("app.demo.expor-codigos é proibido no perfil prod");
        }
        return new VerificarTelefone(pacientes, codigos, protetor, envio, contexto, provedorAtor, auditoria, relogio,
                exporCodigos);
    }
}
