package br.com.clinica.pacientes.infrastructure;

import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Cpf;
import br.com.clinica.compartilhado.domain.Email;
import br.com.clinica.compartilhado.domain.Telefone;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
 * Pacientes fictícios do modo demonstração (CPFs válidos, mas inventados). Carla e Diego já vêm
 * vinculados às contas de teste com o celular verificado, prontos para agendar pelo portal; os demais
 * são pacientes cadastrados pela recepção. Elisa fica sem cadastro para testar "completar cadastro".
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.demo.ativo", havingValue = "true")
class SementeDemoPacientes implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SementeDemoPacientes.class);
    private static final Cpf CPF_CARLA = new Cpf("52601815906");

    private final PacienteRepositorio pacientes;
    private final ContextoClinica contexto;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final String contaCarla;
    private final String contaDiego;

    SementeDemoPacientes(PacienteRepositorio pacientes, ContextoClinica contexto, TransactionTemplate transacao,
                         Clock relogio, @Value("${app.demo.contas.carla:}") String contaCarla,
                         @Value("${app.demo.contas.diego:}") String contaDiego) {
        this.pacientes = pacientes;
        this.contexto = contexto;
        this.transacao = transacao;
        this.relogio = relogio;
        this.contaCarla = contaCarla;
        this.contaDiego = contaDiego;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClinicaId clinica = contexto.atual();
        Instant agora = relogio.instant();
        transacao.executeWithoutResult(status -> {
            if (pacientes.buscarPorCpf(clinica, CPF_CARLA).isPresent()) {
                return;
            }
            salvar(clinica, dados("Carla Mendes", "1988-03-14", CPF_CARLA), conta(contaCarla),
                    List.of(Contato.celular(Telefone.de("(69) 99201-0001"), true).verificadoEm(agora),
                            Contato.email(new Email("carla@exemplo.com"), false, Optional.of(agora))));
            salvar(clinica, dados("Diego Ramos", "1979-11-02", new Cpf("08301661305")), conta(contaDiego),
                    List.of(Contato.celular(Telefone.de("(69) 99201-0002"), true).verificadoEm(agora)));
            salvar(clinica, dados("Helena Prado", "1965-06-21", new Cpf("18609139034")), Optional.empty(),
                    List.of(Contato.celular(Telefone.de("(69) 99201-0003"), true)));
            salvar(clinica, dados("Igor Nunes", "2001-01-30", new Cpf("99603082430")), Optional.empty(),
                    List.of(Contato.celular(Telefone.de("(69) 99201-0004"), true)));
            salvar(clinica, dados("Júlia Castro", "1994-09-09", new Cpf("62819482112")), Optional.empty(),
                    List.of(Contato.celular(Telefone.de("(69) 99201-0005"), true)));
            salvar(clinica, dados("Marcos Teixeira", "1957-12-17", new Cpf("99351819019")), Optional.empty(),
                    List.of(Contato.celular(Telefone.de("(69) 99201-0006"), true)));
            LOG.info("Demonstração: pacientes fictícios criados");
        });
    }

    private void salvar(ClinicaId clinica, Paciente.DadosPessoais dados, Optional<String> conta,
                        List<Contato> contatos) {
        pacientes.salvar(Paciente.novo(clinica, dados, conta.isPresent() ? Paciente.Origem.PORTAL
                : Paciente.Origem.RECEPCAO, conta, contatos));
    }

    private static Paciente.DadosPessoais dados(String nome, String nascimento, Cpf cpf) {
        return new Paciente.DadosPessoais(nome, Optional.empty(), LocalDate.parse(nascimento), cpf, Optional.empty(),
                Paciente.Canal.WHATSAPP);
    }

    private static Optional<String> conta(String sub) {
        return Optional.of(sub).filter(c -> !c.isBlank());
    }
}
