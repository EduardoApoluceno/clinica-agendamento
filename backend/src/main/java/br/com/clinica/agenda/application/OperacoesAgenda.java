package br.com.clinica.agenda.application;

import br.com.clinica.agenda.EventosAgenda.ReservaExpirada;
import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.application.AgendaPortas.Transicao;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.FaixaTrabalho;
import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.clinica.RegrasAgenda;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.profissionais.CatalogoAgenda;
import br.com.clinica.profissionais.CatalogoAgenda.AusenciaAgenda;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Passos comuns aos casos de uso que ocupam ou alteram horários: travar a agenda do profissional,
 * conferir expediente/ausências, liberar holds vencidos e gravar a transição (histórico + auditoria).
 * Sempre chamados dentro da transação do caso de uso.
 */
public class OperacoesAgenda {

    private final AgendamentoRepositorio repositorio;
    private final CatalogoAgenda catalogo;
    private final RegrasAgenda regras;
    private final ContextoClinica contexto;
    private final ProvedorAtor provedorAtor;
    private final RegistradorAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public OperacoesAgenda(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo, RegrasAgenda regras,
                           Contexto infra, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.regras = regras;
        this.contexto = infra.contexto();
        this.provedorAtor = infra.provedorAtor();
        this.auditoria = infra.auditoria();
        this.eventos = eventos;
        this.relogio = infra.relogio();
    }

    Instant agora() {
        return relogio.instant();
    }

    ClinicaId clinica() {
        return contexto.atual();
    }

    RegrasAgenda.Parametros parametros() {
        return regras.parametros();
    }

    void publicar(Object evento) {
        eventos.publishEvent(evento);
    }

    /**
     * Antes de gravar um período: trava a agenda do profissional, confere que ele atende no período e
     * expira holds vencidos que ainda o ocupam (o job pode não ter passado). {@code proprio} é o
     * agendamento sendo remarcado, que não conta como colisão.
     */
    void prepararHorario(UUID profissional, Periodo periodo, Optional<UUID> proprio) {
        repositorio.travarAgendaDoProfissional(contexto.atual(), profissional);
        List<FaixaTrabalho> faixas = catalogo.expediente(profissional).stream()
                .map(f -> new FaixaTrabalho(f.dia(), f.inicio(), f.fim())).toList();
        List<Periodo> ausencias = catalogo.ausencias(profissional, periodo).stream()
                .map(AusenciaAgenda::periodo).toList();
        if (!CalculadoraDisponibilidade.atendeNoPeriodo(periodo, faixas, ausencias, parametros().fuso())) {
            throw new DadoInvalidoException("fora-do-expediente",
                    "O profissional não atende neste horário. Escolha um horário da agenda.");
        }
        Instant agora = agora();
        repositorio.ocupando(contexto.atual(), profissional, periodo).stream()
                .filter(a -> proprio.filter(a.id()::equals).isEmpty())
                .filter(a -> a.reservaVencida(agora))
                .forEach(a -> expirar(a, agora));
    }

    void expirar(Agendamento reserva, Instant agora) {
        aplicar(reserva, reserva.expirado(agora), "RESERVA_EXPIRADA", Optional.of("Prazo de pagamento encerrado"));
        publicar(new ReservaExpirada(reserva.id(), reserva.profissionalId(), reserva.pacienteId(),
                reserva.periodo().inicio()));
    }

    /** Grava a nova versão, a linha de histórico e o evento de auditoria. */
    Agendamento aplicar(Agendamento antes, Agendamento depois, String acaoAuditoria, Optional<String> motivo) {
        Agendamento salvo = repositorio.atualizar(depois);
        boolean mudouPeriodo = !antes.periodo().equals(depois.periodo());
        String ator = provedorAtor.atual().sub();
        repositorio.registrarHistorico(depois.clinicaId(), new Transicao(depois.id(), Optional.of(antes.status()),
                depois.status(), mudouPeriodo ? Optional.of(antes.periodo()) : Optional.empty(),
                mudouPeriodo ? Optional.of(depois.periodo()) : Optional.empty(), ator, motivo, agora()));
        auditoria.registrar(EventoAuditoria.de(acaoAuditoria, "AGENDAMENTO", depois.id())
                .doPaciente(depois.pacienteId())
                .com(Map.of("status", depois.status().name(), "profissional", depois.profissionalId().toString())));
        return salvo;
    }

    void registrarCriacao(Agendamento novo) {
        repositorio.registrarHistorico(novo.clinicaId(), new Transicao(novo.id(), Optional.empty(), novo.status(),
                Optional.empty(), Optional.of(novo.periodo()), novo.criadoPor(), Optional.empty(), agora()));
        auditoria.registrar(EventoAuditoria.de("AGENDAMENTO_CRIADO", "AGENDAMENTO", novo.id())
                .doPaciente(novo.pacienteId())
                .com(Map.of("status", novo.status().name(), "origem", novo.origem().name(),
                        "profissional", novo.profissionalId().toString())));
    }

    /** Dependências de infraestrutura comuns (agrupadas para manter construtores curtos). */
    public record Contexto(ContextoClinica contexto, ProvedorAtor provedorAtor, RegistradorAuditoria auditoria,
                           Clock relogio) { }
}
