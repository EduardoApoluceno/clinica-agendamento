package br.com.clinica.agenda.infrastructure;

import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.FaixaTrabalho;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.Horario;
import br.com.clinica.agenda.domain.OrigemAgendamento;
import br.com.clinica.clinica.RegrasAgenda;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.profissionais.CatalogoAgenda;
import br.com.clinica.profissionais.CatalogoAgenda.ProfissionalAgendavel;
import br.com.clinica.profissionais.CatalogoAgenda.ServicoAgendavel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Agendamentos fictícios de duas semanas para trás e duas para frente, para a grade e (na Fase 6) os
 * relatórios já aparecerem preenchidos. Só no modo demonstração e só com a agenda vazia. Valores
 * aleatórios com semente fixa: a mesma demonstração a cada {@code down -v}.
 */
@Component
@Order(30)
@ConditionalOnProperty(name = "app.demo.ativo", havingValue = "true")
class SementeDemoAgenda implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SementeDemoAgenda.class);
    private static final int DIAS = 14;
    private static final int PERCENTUAL_OCUPACAO = 45;

    private final AgendamentoRepositorio repositorio;
    private final CatalogoAgenda catalogo;
    private final RegrasAgenda regras;
    private final ContextoClinica contexto;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final Clock relogio;

    SementeDemoAgenda(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo, RegrasAgenda regras,
                      ContextoClinica contexto, JdbcTemplate jdbc, TransactionTemplate transacao, Clock relogio) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.regras = regras;
        this.contexto = contexto;
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClinicaId clinica = contexto.atual();
        Instant agora = relogio.instant();
        ZoneId fuso = regras.parametros().fuso();
        LocalDate hoje = agora.atZone(fuso).toLocalDate();
        Periodo janela = new Periodo(hoje.minusDays(DIAS).atStartOfDay(fuso).toInstant(),
                hoje.plusDays(DIAS + 1L).atStartOfDay(fuso).toInstant());
        transacao.executeWithoutResult(status -> {
            if (!repositorio.iniciandoEm(clinica, Optional.empty(), janela).isEmpty()) {
                return;
            }
            // Só para a semente: ids dos pacientes fictícios (o módulo pacientes não expõe listagem).
            List<UUID> pacientes = jdbc.queryForList("SELECT id FROM paciente WHERE clinica_id = ? ORDER BY nome",
                    UUID.class, clinica.valor());
            if (pacientes.isEmpty()) {
                return;
            }
            Semeadura semeadura = new Semeadura(clinica, agora, fuso, hoje, pacientes, new Random(42));
            int criados = catalogo.profissionaisAtivos().stream().mapToInt(p -> semear(semeadura, p)).sum();
            LOG.info("Demonstração: {} agendamentos fictícios criados", criados);
        });
    }

    private int semear(Semeadura s, ProfissionalAgendavel profissional) {
        Optional<ServicoAgendavel> servico = profissional.servicos().stream().map(catalogo::servico)
                .flatMap(Optional::stream).filter(sv -> sv.valorAntecipado().centavos() == 0)
                .max((a, b) -> a.duracao().compareTo(b.duracao()));
        if (servico.isEmpty()) {
            return 0;
        }
        List<FaixaTrabalho> faixas = catalogo.expediente(profissional.id()).stream()
                .map(f -> new FaixaTrabalho(f.dia(), f.inicio(), f.fim())).toList();
        int criados = 0;
        for (LocalDate dia = s.hoje().minusDays(DIAS); !dia.isAfter(s.hoje().plusDays(DIAS)); dia = dia.plusDays(1)) {
            List<Horario> horarios = CalculadoraDisponibilidade.horarios(new CalculadoraDisponibilidade.Pedido(dia, dia,
                    s.fuso(), faixas, List.of(), List.of(), servico.get().duracao(), Instant.EPOCH, Instant.MAX));
            for (Horario h : horarios) {
                if (s.sorteio().nextInt(100) < PERCENTUAL_OCUPACAO) {
                    UUID paciente = s.pacientes().get(s.sorteio().nextInt(s.pacientes().size()));
                    repositorio.inserir(s.estadoFinal(profissional.id(), paciente, servico.get(), h.periodo()));
                    criados++;
                }
            }
        }
        return criados;
    }

    /** Parâmetros de uma execução da semente. */
    private record Semeadura(ClinicaId clinica, Instant agora, ZoneId fuso, LocalDate hoje, List<UUID> pacientes,
                             Random sorteio) {

        /** Passado: realizado, falta ou cancelado. Futuro: confirmado ou aguardando confirmação. */
        Agendamento estadoFinal(UUID profissional, UUID paciente, ServicoAgendavel servico, Periodo periodo) {
            Instant criadoEm = periodo.inicio().minus(Duration.ofDays(3));
            Agendamento a = Agendamento.novo(new Agendamento.Dados(clinica, profissional, paciente, servico.id(),
                    periodo, sorteio.nextBoolean() ? OrigemAgendamento.PORTAL : OrigemAgendamento.RECEPCAO,
                    servico.preco(), servico.valorAntecipado(), Optional.empty(), "demonstracao"),
                    criadoEm, Duration.ofMinutes(15));
            int sorte = sorteio.nextInt(100);
            if (periodo.fim().isBefore(agora)) {
                if (sorte < 12) {
                    return a.cancelado(criadoEm.plus(Duration.ofDays(1)), Optional.of("Imprevisto do paciente"));
                }
                return sorte < 25 ? a.falta(periodo.inicio()) : a.confirmado().presente(periodo.inicio(), fuso).realizado();
            }
            return sorte < 55 ? a.confirmado() : a;
        }
    }
}
