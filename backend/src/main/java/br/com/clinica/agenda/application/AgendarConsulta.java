package br.com.clinica.agenda.application;

import br.com.clinica.agenda.EventosAgenda.AgendamentoCriado;
import br.com.clinica.agenda.application.AcessoAgenda.Acesso;
import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.OrigemAgendamento;
import br.com.clinica.agenda.domain.PoliticaAntecedencia;
import br.com.clinica.clinica.RegrasAgenda.Parametros;
import br.com.clinica.compartilhado.domain.AcessoNegadoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.pacientes.ConsultaPacientes;
import br.com.clinica.pacientes.ConsultaPacientes.PacienteAgenda;
import br.com.clinica.profissionais.CatalogoAgenda;
import br.com.clinica.profissionais.CatalogoAgenda.ServicoAgendavel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca um horário. O paciente agenda para si (com celular verificado e respeitando a antecedência);
 * a recepção agenda para qualquer paciente. A colisão entre requisições simultâneas é barrada pela
 * constraint do banco (ADR-0004) e vira 409.
 */
public class AgendarConsulta {

    private final AgendamentoRepositorio repositorio;
    private final CatalogoAgenda catalogo;
    private final ConsultaPacientes pacientes;
    private final AcessoAgenda acesso;
    private final OperacoesAgenda operacoes;
    private final boolean cobrancaAntecipada;

    /**
     * @param cobrancaAntecipada com {@code false} (até o módulo de pagamentos, Fase 4) o sinal do serviço não é
     *     exigido e o horário não fica em hold: não haveria como pagar antes de a reserva expirar
     */
    public AgendarConsulta(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo, ConsultaPacientes pacientes,
                           AcessoAgenda acesso, OperacoesAgenda operacoes, boolean cobrancaAntecipada) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.pacientes = pacientes;
        this.acesso = acesso;
        this.operacoes = operacoes;
        this.cobrancaAntecipada = cobrancaAntecipada;
    }

    @Transactional
    public Agendamento agendar(Pedido pedido) {
        Acesso a = acesso.atual();
        PacienteAgenda paciente = pacienteDoPedido(a, pedido.paciente());
        ServicoAgendavel servico = catalogo.servico(pedido.servico()).filter(ServicoAgendavel::ativo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico"));
        catalogo.profissional(pedido.profissional()).filter(p -> p.atende(servico.id()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional"));

        Parametros parametros = operacoes.parametros();
        Instant agora = operacoes.agora();
        if (a.equipe()) {
            PoliticaAntecedencia.exigirFuturo(agora, pedido.inicio());
        } else {
            politica(parametros).exigirNovoHorario(agora, pedido.inicio());
        }
        Periodo periodo = new Periodo(pedido.inicio(), pedido.inicio().plus(servico.duracao()));
        operacoes.prepararHorario(pedido.profissional(), periodo, Optional.empty());

        Agendamento novo = Agendamento.novo(new Agendamento.Dados(operacoes.clinica(), pedido.profissional(),
                paciente.id(), servico.id(), periodo, a.equipe() ? OrigemAgendamento.RECEPCAO : OrigemAgendamento.PORTAL,
                servico.preco(), cobrancaAntecipada ? servico.valorAntecipado() : Dinheiro.ZERO, pedido.observacao(),
                a.ator().sub()),
                agora, parametros.hold());
        repositorio.inserir(novo);
        operacoes.registrarCriacao(novo);
        operacoes.publicar(new AgendamentoCriado(novo.id(), novo.profissionalId(), novo.pacienteId(), novo.servicoId(),
                periodo.inicio(), periodo.fim(), novo.status().name(), novo.origem().name()));
        return novo;
    }

    static PoliticaAntecedencia politica(Parametros p) {
        return new PoliticaAntecedencia(p.antecedenciaAgendamento(), p.antecedenciaAlteracao(), p.horizonte());
    }

    private PacienteAgenda pacienteDoPedido(Acesso a, Optional<UUID> informado) {
        return switch (a.perfil()) {
            case EQUIPE -> informado.flatMap(pacientes::porId).orElseThrow(() -> informado.isEmpty()
                    ? new DadoInvalidoException("paciente-obrigatorio", "Selecione o paciente.")
                    : new RecursoNaoEncontradoException("paciente"));
            case PACIENTE -> {
                PacienteAgenda proprio = a.paciente().orElseThrow(() -> new DadoInvalidoException("cadastro-incompleto",
                        "Complete seu cadastro em \"Meu cadastro\" antes de agendar."));
                if (!proprio.celularVerificado()) {
                    throw new DadoInvalidoException("celular-nao-verificado",
                            "Confirme seu celular em \"Meu cadastro\" antes de agendar.");
                }
                yield proprio;
            }
            case PROFISSIONAL -> throw new AcessoNegadoException("agenda-alteracao-negada",
                    "Novos agendamentos são feitos pela recepção.");
        };
    }

    /** @param paciente obrigatório para a recepção; ignorado para o paciente (sempre ele mesmo) */
    public record Pedido(UUID servico, UUID profissional, Instant inicio, Optional<UUID> paciente,
                         Optional<String> observacao) { }
}
