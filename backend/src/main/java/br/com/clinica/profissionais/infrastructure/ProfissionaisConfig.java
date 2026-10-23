package br.com.clinica.profissionais.infrastructure;

import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.profissionais.application.GerirAgendaProfissional;
import br.com.clinica.profissionais.application.GerirProfissionais;
import br.com.clinica.profissionais.application.GerirServicos;
import br.com.clinica.profissionais.application.ProfissionaisPortas.AgendaProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.EspecialidadeRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.ProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.TipoServicoRepositorio;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ProfissionaisConfig {

    @Bean
    GerirProfissionais gerirProfissionais(ProfissionalRepositorio profissionais, TipoServicoRepositorio servicos,
                                          EspecialidadeRepositorio especialidades, ContextoClinica contexto,
                                          ProvedorAtor provedorAtor, RegistradorAuditoria auditoria) {
        return new GerirProfissionais(profissionais, servicos, especialidades, contexto, provedorAtor, auditoria);
    }

    @Bean
    GerirServicos gerirServicos(TipoServicoRepositorio servicos, EspecialidadeRepositorio especialidades,
                                ContextoClinica contexto, RegistradorAuditoria auditoria) {
        return new GerirServicos(servicos, especialidades, contexto, auditoria);
    }

    @Bean
    GerirAgendaProfissional gerirAgendaProfissional(ProfissionalRepositorio profissionais,
                                                    AgendaProfissionalRepositorio agenda, ContextoClinica contexto,
                                                    ProvedorAtor provedorAtor, RegistradorAuditoria auditoria) {
        return new GerirAgendaProfissional(profissionais, agenda, contexto, provedorAtor, auditoria);
    }
}
