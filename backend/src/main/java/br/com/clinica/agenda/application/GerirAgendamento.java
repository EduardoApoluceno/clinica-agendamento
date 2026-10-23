package br.com.clinica.agenda.application;

import br.com.clinica.agenda.EventosAgenda.AgendamentoAtualizado;
import br.com.clinica.agenda.EventosAgenda.AgendamentoCancelado;
import br.com.clinica.agenda.EventosAgenda.AgendamentoConfirmado;
import br.com.clinica.agenda.EventosAgenda.AgendamentoRemarcado;
import br.com.clinica.agenda.EventosAgenda.VagaLiberada;
import br.com.clinica.agenda.application.AcessoAgenda.Acesso;
import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.PoliticaAntecedencia;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remarcar, cancelar, confirmar e conduzir o atendimento (presença, falta, conclusão). Remarcação e
 * cancelamento não têm penalidade; o paciente só precisa respeitar a antecedência configurada.
 */
public class GerirAgendamento {

    private final AgendamentoRepositorio repositorio;
    private final AcessoAgenda acesso;
    private final OperacoesAgenda operacoes;

    public GerirAgendamento(AgendamentoRepositorio repositorio, AcessoAgenda acesso, OperacoesAgenda operacoes) {
        this.repositorio = repositorio;
        this.acesso = acesso;
        this.operacoes = operacoes;
    }

    /** @param versao versão que a tela mostrava; se outra pessoa alterou antes, 409 */
    @Transactional
    public Agendamento remarcar(UUID id, Instant novoInicio, long versao) {
        Acesso a = acesso.atual();
        a.exigirAlterar();
        Agendamento atual = carregar(a, id);
        if (atual.versao() != versao) {
            throw new ConflitoException("edicao-concorrente", "O agendamento foi alterado por outra pessoa. Recarregue.");
        }
        Instant agora = operacoes.agora();
        if (a.equipe()) {
            PoliticaAntecedencia.exigirFuturo(agora, novoInicio);
        } else {
            PoliticaAntecedencia politica = AgendarConsulta.politica(operacoes.parametros());
            politica.exigirAlteracao(agora, atual.periodo().inicio());
            politica.exigirNovoHorario(agora, novoInicio);
        }
        Duration duracao = Duration.between(atual.periodo().inicio(), atual.periodo().fim());
        Periodo novo = new Periodo(novoInicio, novoInicio.plus(duracao));
        operacoes.prepararHorario(atual.profissionalId(), novo, Optional.of(atual.id()));
        Agendamento salvo = operacoes.aplicar(atual, atual.remarcadoPara(novo), "AGENDAMENTO_REMARCADO",
                Optional.empty());
        operacoes.publicar(new AgendamentoRemarcado(salvo.id(), salvo.profissionalId(), salvo.pacienteId(),
                atual.periodo().inicio(), atual.periodo().fim(), novo.inicio(), novo.fim()));
        operacoes.publicar(new VagaLiberada(atual.profissionalId(), atual.servicoId(), atual.periodo().inicio(),
                atual.periodo().fim()));
        return salvo;
    }

    @Transactional
    public Agendamento cancelar(UUID id, Optional<String> motivo) {
        Acesso a = acesso.atual();
        a.exigirAlterar();
        Agendamento atual = carregar(a, id);
        Instant agora = operacoes.agora();
        if (!a.equipe()) {
            AgendarConsulta.politica(operacoes.parametros()).exigirAlteracao(agora, atual.periodo().inicio());
        }
        Agendamento salvo = operacoes.aplicar(atual, atual.cancelado(agora, motivo), "AGENDAMENTO_CANCELADO", motivo);
        operacoes.publicar(new AgendamentoCancelado(salvo.id(), salvo.profissionalId(), salvo.pacienteId(),
                salvo.periodo().inicio(), salvo.periodo().fim(), a.equipe(), salvo.cancelamento().flatMap(
                        Agendamento.Cancelamento::motivo)));
        if (atual.periodo().inicio().isAfter(agora)) {
            operacoes.publicar(new VagaLiberada(salvo.profissionalId(), salvo.servicoId(), salvo.periodo().inicio(),
                    salvo.periodo().fim()));
        }
        return salvo;
    }

    /** Paciente (do lembrete ou do portal) ou recepção confirmam que o paciente vem. */
    @Transactional
    public Agendamento confirmar(UUID id) {
        Acesso a = acesso.atual();
        a.exigirAlterar();
        Agendamento atual = carregar(a, id);
        Agendamento confirmado = atual.confirmado();
        if (confirmado == atual) {
            return atual;
        }
        Agendamento salvo = operacoes.aplicar(atual, confirmado, "AGENDAMENTO_CONFIRMADO", Optional.empty());
        operacoes.publicar(new AgendamentoConfirmado(salvo.id(), salvo.profissionalId(), salvo.pacienteId(),
                salvo.periodo().inicio()));
        return salvo;
    }

    @Transactional
    public Agendamento registrarPresenca(UUID id) {
        return conduzir(id, "PRESENCA_REGISTRADA", a -> a.presente(operacoes.agora(), operacoes.parametros().fuso()));
    }

    @Transactional
    public Agendamento registrarFalta(UUID id) {
        return conduzir(id, "FALTA_REGISTRADA", a -> a.falta(operacoes.agora()));
    }

    @Transactional
    public Agendamento concluir(UUID id) {
        return conduzir(id, "ATENDIMENTO_CONCLUIDO", Agendamento::realizado);
    }

    private Agendamento conduzir(UUID id, String acao, UnaryOperator<Agendamento> transicao) {
        Acesso a = acesso.atual();
        a.exigirConduzir();
        Agendamento atual = carregar(a, id);
        Agendamento salvo = operacoes.aplicar(atual, transicao.apply(atual), acao, Optional.empty());
        operacoes.publicar(new AgendamentoAtualizado(salvo.id(), salvo.profissionalId(), salvo.status().name()));
        return salvo;
    }

    private Agendamento carregar(Acesso a, UUID id) {
        return a.exigirVisivel(repositorio.buscar(operacoes.clinica(), id));
    }
}
