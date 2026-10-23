package br.com.clinica.agenda.infrastructure;

import br.com.clinica.agenda.application.AcessoAgenda;
import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.application.AgendarConsulta;
import br.com.clinica.agenda.application.ConsultarAgenda;
import br.com.clinica.agenda.application.ConsultarDisponibilidade;
import br.com.clinica.agenda.application.ExpirarReservas;
import br.com.clinica.agenda.application.GerirAgendamento;
import br.com.clinica.agenda.application.OperacoesAgenda;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.clinica.RegrasAgenda;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.pacientes.ConsultaPacientes;
import br.com.clinica.profissionais.CatalogoAgenda;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AgendaConfig {

    @Bean
    AcessoAgenda acessoAgenda(ProvedorAtor provedorAtor, ConsultaPacientes pacientes, CatalogoAgenda catalogo) {
        return new AcessoAgenda(provedorAtor, pacientes, catalogo);
    }

    @Bean
    OperacoesAgenda operacoesAgenda(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo, RegrasAgenda regras,
                                    ContextoClinica contexto, ProvedorAtor provedorAtor,
                                    RegistradorAuditoria auditoria, Clock relogio, ApplicationEventPublisher eventos) {
        return new OperacoesAgenda(repositorio, catalogo, regras,
                new OperacoesAgenda.Contexto(contexto, provedorAtor, auditoria, relogio), eventos);
    }

    @Bean
    AgendarConsulta agendarConsulta(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo,
                                    ConsultaPacientes pacientes, AcessoAgenda acesso, OperacoesAgenda operacoes,
                                    @Value("${app.agenda.cobranca-antecipada:false}") boolean cobrancaAntecipada) {
        return new AgendarConsulta(repositorio, catalogo, pacientes, acesso, operacoes, cobrancaAntecipada);
    }

    @Bean
    GerirAgendamento gerirAgendamento(AgendamentoRepositorio repositorio, AcessoAgenda acesso,
                                      OperacoesAgenda operacoes) {
        return new GerirAgendamento(repositorio, acesso, operacoes);
    }

    @Bean
    ConsultarDisponibilidade consultarDisponibilidade(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo,
                                                      ProvedorAtor provedorAtor, OperacoesAgenda operacoes) {
        return new ConsultarDisponibilidade(repositorio, catalogo, provedorAtor, operacoes);
    }

    @Bean
    ConsultarAgenda consultarAgenda(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo,
                                    ConsultaPacientes pacientes, AcessoAgenda acesso, OperacoesAgenda operacoes) {
        return new ConsultarAgenda(repositorio, catalogo, pacientes, acesso, operacoes);
    }

    @Bean
    ExpirarReservas expirarReservas(AgendamentoRepositorio repositorio, OperacoesAgenda operacoes) {
        return new ExpirarReservas(repositorio, operacoes);
    }

    /** A cada minuto (job persistido: roda em uma só instância). A disponibilidade já ignora holds vencidos. */
    @Bean
    RecurringTask<Void> tarefaExpirarReservas(ExpirarReservas caso) {
        return Tasks.recurring("expirar-reservas", Schedules.fixedDelay(Duration.ofMinutes(1)))
                .execute((instancia, contexto) -> caso.executar());
    }
}
