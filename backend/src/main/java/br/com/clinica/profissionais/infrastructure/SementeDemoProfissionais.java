package br.com.clinica.profissionais.infrastructure;

import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.profissionais.application.ProfissionaisPortas.AgendaProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.EspecialidadeRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.ProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.TipoServicoRepositorio;
import br.com.clinica.profissionais.domain.Expediente;
import br.com.clinica.profissionais.domain.PoliticaPagamento;
import br.com.clinica.profissionais.domain.Profissional;
import br.com.clinica.profissionais.domain.RegistroConselho;
import br.com.clinica.profissionais.domain.TipoServico;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dados fictícios do modo demonstração: serviços, dois profissionais vinculados às contas de teste do
 * Keycloak e o expediente semanal. Só roda com {@code app.demo.ativo} e só se ainda não houver profissionais.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo.ativo", havingValue = "true")
class SementeDemoProfissionais implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SementeDemoProfissionais.class);

    private final ProfissionalRepositorio profissionais;
    private final TipoServicoRepositorio servicos;
    private final EspecialidadeRepositorio especialidades;
    private final AgendaProfissionalRepositorio agenda;
    private final ContextoClinica contexto;
    private final TransactionTemplate transacao;
    private final String contaAna;
    private final String contaBruno;

    SementeDemoProfissionais(ProfissionalRepositorio profissionais, TipoServicoRepositorio servicos,
                             EspecialidadeRepositorio especialidades, AgendaProfissionalRepositorio agenda,
                             ContextoClinica contexto, TransactionTemplate transacao,
                             @Value("${app.demo.contas.dra-ana:}") String contaAna,
                             @Value("${app.demo.contas.dr-bruno:}") String contaBruno) {
        this.profissionais = profissionais;
        this.servicos = servicos;
        this.especialidades = especialidades;
        this.agenda = agenda;
        this.contexto = contexto;
        this.transacao = transacao;
        this.contaAna = contaAna;
        this.contaBruno = contaBruno;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClinicaId clinica = contexto.atual();
        transacao.executeWithoutResult(status -> {
            if (!profissionais.listar(clinica, false).isEmpty()) {
                return;
            }
            UUID clinicaMedica = especialidades.criar(clinica, "Clínica médica").id();
            UUID dermatologia = especialidades.criar(clinica, "Dermatologia").id();
            UUID consulta = servico(clinica, dados("Consulta", "Consulta clínica de rotina.", 30, 25_000,
                    PoliticaPagamento.nenhuma()));
            UUID retorno = servico(clinica, dados("Retorno", "Retorno em até 30 dias após a consulta.", 20, 0,
                    PoliticaPagamento.nenhuma()));
            UUID procedimento = servico(clinica, dados("Procedimento dermatológico",
                    "Pequenos procedimentos em consultório. Reserva com sinal via Pix.", 60, 40_000,
                    new PoliticaPagamento(PoliticaPagamento.Tipo.SINAL, Optional.of(new Dinheiro(10_000)))));

            UUID ana = profissional(clinica, new Profissional.DadosProfissional("Dra. Ana Souza",
                    new RegistroConselho("CRM", "52718", "RO"), conta(contaAna), Set.of(clinicaMedica),
                    Set.of(consulta, retorno)));
            UUID bruno = profissional(clinica, new Profissional.DadosProfissional("Dr. Bruno Lima",
                    new RegistroConselho("CRM", "61904", "RO"), conta(contaBruno), Set.of(dermatologia),
                    Set.of(consulta, procedimento)));
            agenda.substituirExpediente(clinica, ana, semana(LocalTime.of(8, 0), LocalTime.of(12, 0),
                    LocalTime.of(14, 0), LocalTime.of(18, 0)));
            agenda.substituirExpediente(clinica, bruno, semana(LocalTime.of(9, 0), LocalTime.of(13, 0),
                    LocalTime.of(15, 0), LocalTime.of(19, 0)));
            LOG.info("Demonstração: serviços, profissionais e expedientes criados");
        });
    }

    private UUID servico(ClinicaId clinica, TipoServico.DadosServico dados) {
        return servicos.salvar(TipoServico.novo(clinica, dados)).id();
    }

    private static TipoServico.DadosServico dados(String nome, String descricao, int minutos, long centavos,
                                                  PoliticaPagamento politica) {
        return new TipoServico.DadosServico(nome, Optional.of(descricao), Optional.empty(), Duration.ofMinutes(minutos),
                new Dinheiro(centavos), politica);
    }

    private UUID profissional(ClinicaId clinica, Profissional.DadosProfissional dados) {
        return profissionais.salvar(Profissional.novo(clinica, dados)).id();
    }

    private static Optional<String> conta(String sub) {
        return Optional.of(sub).filter(c -> !c.isBlank());
    }

    /** Segunda a sexta, manhã e tarde (o intervalo do almoço é a lacuna entre as faixas). */
    private static Expediente semana(LocalTime inicioManha, LocalTime fimManha, LocalTime inicioTarde,
                                     LocalTime fimTarde) {
        List<Expediente.Faixa> faixas = new ArrayList<>();
        for (DayOfWeek dia : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) {
            faixas.add(new Expediente.Faixa(dia, inicioManha, fimManha));
            faixas.add(new Expediente.Faixa(dia, inicioTarde, fimTarde));
        }
        return new Expediente(faixas);
    }
}
