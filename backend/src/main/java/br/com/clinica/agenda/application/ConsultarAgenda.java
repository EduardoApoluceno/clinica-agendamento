package br.com.clinica.agenda.application;

import br.com.clinica.agenda.application.AcessoAgenda.Acesso;
import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.FaixaTrabalho;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.Horario;
import br.com.clinica.agenda.domain.StatusAgendamento;
import br.com.clinica.compartilhado.domain.AcessoNegadoException;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.pacientes.ConsultaPacientes;
import br.com.clinica.pacientes.ConsultaPacientes.PacienteAgenda;
import br.com.clinica.profissionais.CatalogoAgenda;
import br.com.clinica.profissionais.CatalogoAgenda.AusenciaAgenda;
import br.com.clinica.profissionais.CatalogoAgenda.ProfissionalAgendavel;
import br.com.clinica.profissionais.CatalogoAgenda.ServicoAgendavel;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Leituras da agenda: grade do dia (recepção/profissional), "meus agendamentos" (paciente) e detalhe. */
public class ConsultarAgenda {

    /** Granularidade dos horários livres mostrados na grade da recepção. */
    static final Duration PASSO_GRADE = Duration.ofMinutes(30);
    private static final int LIMITE_MEUS = 50;

    private final AgendamentoRepositorio repositorio;
    private final CatalogoAgenda catalogo;
    private final ConsultaPacientes pacientes;
    private final AcessoAgenda acesso;
    private final OperacoesAgenda operacoes;

    public ConsultarAgenda(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo, ConsultaPacientes pacientes,
                           AcessoAgenda acesso, OperacoesAgenda operacoes) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.pacientes = pacientes;
        this.acesso = acesso;
        this.operacoes = operacoes;
    }

    /** Uma coluna por profissional ativo; o profissional vê só a própria coluna. */
    @Transactional(readOnly = true)
    public AgendaDoDia dia(LocalDate data) {
        Acesso a = acesso.atual();
        if (a.perfil() == AcessoAgenda.Perfil.PACIENTE) {
            throw new AcessoNegadoException("agenda-sem-acesso", "A grade da agenda é da equipe da clínica.");
        }
        ZoneId fuso = operacoes.parametros().fuso();
        Periodo janela = new Periodo(data.atStartOfDay(fuso).toInstant(), data.plusDays(1).atStartOfDay(fuso).toInstant());
        Nomes nomes = new Nomes();
        List<Coluna> colunas = catalogo.profissionaisAtivos().stream()
                .filter(p -> a.equipe() || a.profissional().filter(p.id()::equals).isPresent())
                .map(p -> coluna(p, data, janela, nomes))
                .toList();
        return new AgendaDoDia(data, colunas, resumo(colunas));
    }

    @Transactional(readOnly = true)
    public List<Item> meus() {
        Acesso a = acesso.atual();
        Optional<PacienteAgenda> paciente = a.paciente();
        if (paciente.isEmpty()) {
            return List.of();
        }
        Nomes nomes = new Nomes();
        return repositorio.doPaciente(operacoes.clinica(), paciente.get().id(), LIMITE_MEUS).stream()
                .map(nomes::item).toList();
    }

    @Transactional(readOnly = true)
    public Item detalhe(UUID id) {
        Acesso a = acesso.atual();
        return new Nomes().item(a.exigirVisivel(repositorio.buscar(operacoes.clinica(), id)));
    }

    private Coluna coluna(ProfissionalAgendavel profissional, LocalDate data, Periodo janela, Nomes nomes) {
        ZoneId fuso = operacoes.parametros().fuso();
        Instant agora = operacoes.agora();
        List<FaixaTrabalho> faixas = catalogo.expediente(profissional.id()).stream()
                .map(f -> new FaixaTrabalho(f.dia(), f.inicio(), f.fim())).toList();
        List<AusenciaAgenda> ausencias = catalogo.ausencias(profissional.id(), janela);
        List<Agendamento> doDia = repositorio.iniciandoEm(operacoes.clinica(), Optional.of(profissional.id()), janela)
                .stream().filter(ag -> ag.status().ocupaHorario() && !ag.reservaVencida(agora)).toList();
        List<Periodo> livres = CalculadoraDisponibilidade.horarios(new CalculadoraDisponibilidade.Pedido(data, data,
                        fuso, faixas, ausencias.stream().map(AusenciaAgenda::periodo).toList(),
                        doDia.stream().map(Agendamento::periodo).toList(), PASSO_GRADE, agora, janela.fim()))
                .stream().filter(Horario::livre).map(Horario::periodo).toList();
        List<Periodo> expediente = faixas.stream().filter(f -> f.dia() == data.getDayOfWeek())
                .map(f -> new Periodo(data.atTime(f.inicio()).atZone(fuso).toInstant(),
                        data.atTime(f.fim()).atZone(fuso).toInstant()))
                .toList();
        nomes.profissionais.put(profissional.id(), profissional.nome());
        return new Coluna(profissional.id(), profissional.nome(), expediente, ausencias,
                doDia.stream().map(nomes::item).toList(), livres);
    }

    private static Resumo resumo(List<Coluna> colunas) {
        List<Agendamento> todos = colunas.stream().flatMap(c -> c.agendamentos().stream())
                .map(Item::agendamento).toList();
        long aguardando = todos.stream().filter(a -> a.status() == StatusAgendamento.AGUARDANDO_CONFIRMACAO
                || a.status() == StatusAgendamento.RESERVADO).count();
        return new Resumo(todos.size(), (int) aguardando, colunas.stream().mapToInt(c -> c.livres().size()).sum());
    }

    /** Resolve nomes de paciente, serviço e profissional uma vez por consulta. */
    private final class Nomes {
        private final Map<UUID, String> pacientesPorId = new HashMap<>();
        private final Map<UUID, String> servicos = new HashMap<>();
        private final Map<UUID, String> profissionais = new HashMap<>();

        Item item(Agendamento a) {
            String paciente = pacientesPorId.computeIfAbsent(a.pacienteId(),
                    id -> pacientes.porId(id).map(PacienteAgenda::nome).orElse("—"));
            String servico = servicos.computeIfAbsent(a.servicoId(),
                    id -> catalogo.servico(id).map(ServicoAgendavel::nome).orElse("—"));
            String profissional = profissionais.computeIfAbsent(a.profissionalId(),
                    id -> catalogo.profissional(id).map(ProfissionalAgendavel::nome).orElse("—"));
            return new Item(a, paciente, servico, profissional);
        }
    }

    public record AgendaDoDia(LocalDate data, List<Coluna> colunas, Resumo resumo) { }

    /**
     * @param expediente faixas de trabalho do dia
     * @param livres horários livres de {@link #PASSO_GRADE} que ainda não passaram
     */
    public record Coluna(UUID profissionalId, String profissional, List<Periodo> expediente,
                         List<AusenciaAgenda> ausencias, List<Item> agendamentos, List<Periodo> livres) { }

    public record Item(Agendamento agendamento, String paciente, String servico, String profissional) { }

    /** @param livres soma dos horários livres de 30 min do dia em todas as colunas */
    public record Resumo(int agendados, int aguardandoConfirmacao, int livres) { }
}
