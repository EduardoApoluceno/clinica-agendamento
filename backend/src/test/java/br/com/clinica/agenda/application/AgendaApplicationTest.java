package br.com.clinica.agenda.application;

import br.com.clinica.Fixtures;
import br.com.clinica.agenda.EventosAgenda.AgendamentoCancelado;
import br.com.clinica.agenda.EventosAgenda.AgendamentoConfirmado;
import br.com.clinica.agenda.EventosAgenda.AgendamentoCriado;
import br.com.clinica.agenda.EventosAgenda.AgendamentoRemarcado;
import br.com.clinica.agenda.EventosAgenda.ReservaExpirada;
import br.com.clinica.agenda.EventosAgenda.VagaLiberada;
import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.application.AgendaPortas.Transicao;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.HorarioIndisponivelException;
import br.com.clinica.agenda.domain.OrigemAgendamento;
import br.com.clinica.agenda.domain.StatusAgendamento;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.clinica.RegrasAgenda;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.AcessoNegadoException;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.pacientes.ConsultaPacientes;
import br.com.clinica.pacientes.ConsultaPacientes.PacienteAgenda;
import br.com.clinica.profissionais.CatalogoAgenda;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AgendaApplicationTest {

    static final ZoneId FUSO = ZoneId.of("America/Porto_Velho");
    /** Fixtures.AGORA: quinta-feira 22/10/2026, 08:00 em Porto Velho. */
    static final LocalDate SEXTA = LocalDate.of(2026, 10, 23);
    static final UUID CONSULTA = UUID.randomUUID();
    static final UUID COM_SINAL = UUID.randomUUID();
    static final UUID ANA = UUID.randomUUID();
    static final UUID BRUNO = UUID.randomUUID();
    static final PacienteAgenda CARLA = new PacienteAgenda(UUID.randomUUID(), "Carla", Optional.of("carla"), true);
    static final PacienteAgenda RUI = new PacienteAgenda(UUID.randomUUID(), "Rui", Optional.of("rui"), false);

    static Instant em(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(FUSO).toInstant();
    }

    private final RepositorioEmMemoria repositorio = new RepositorioEmMemoria();
    private final CatalogoFalso catalogo = new CatalogoFalso();
    private final List<Object> eventos = new ArrayList<>();

    private final ConsultaPacientes pacientes = new ConsultaPacientes() {
        @Override
        public Optional<PacienteAgenda> porId(UUID id) {
            return Set.of(CARLA, RUI).stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public Optional<PacienteAgenda> porUsuario(String sub) {
            return Set.of(CARLA, RUI).stream().filter(p -> p.usuarioSub().orElseThrow().equals(sub)).findFirst();
        }
    };

    private final RegrasAgenda regras = () -> new RegrasAgenda.Parametros(FUSO, Duration.ofHours(1),
            Duration.ofHours(2), Duration.ofDays(60), Duration.ofMinutes(15));

    /** Casos de uso montados para um ator e um relógio. */
    record Casos(AgendarConsulta agendar, GerirAgendamento gerir, ConsultarDisponibilidade disponibilidade,
                 ConsultarAgenda consultar, ExpirarReservas expirar) { }

    Casos como(ProvedorAtor ator) {
        return como(ator, Fixtures.RELOGIO);
    }

    Casos como(ProvedorAtor ator, Clock relogio) {
        OperacoesAgenda operacoes = new OperacoesAgenda(repositorio, catalogo, regras,
                new OperacoesAgenda.Contexto(Fixtures.CONTEXTO, ator, mock(RegistradorAuditoria.class), relogio),
                eventos::add);
        AcessoAgenda acesso = new AcessoAgenda(ator, pacientes, catalogo);
        return new Casos(new AgendarConsulta(repositorio, catalogo, pacientes, acesso, operacoes, true),
                new GerirAgendamento(repositorio, acesso, operacoes),
                new ConsultarDisponibilidade(repositorio, catalogo, ator, operacoes),
                new ConsultarAgenda(repositorio, catalogo, pacientes, acesso, operacoes),
                new ExpirarReservas(repositorio, operacoes));
    }

    final Casos recepcao = como(Fixtures.ator("recepcao", Papel.RECEPCAO));
    final Casos carla = como(Fixtures.ator("carla", Papel.PACIENTE));
    final Casos ana = como(Fixtures.ator("dra.ana", Papel.PROFISSIONAL));

    static AgendarConsulta.Pedido pedido(UUID servico, UUID profissional, Instant inicio, PacienteAgenda paciente) {
        return new AgendarConsulta.Pedido(servico, profissional, inicio, Optional.of(paciente.id()), Optional.empty());
    }

    Agendamento agendarSexta(int hora, int minuto) {
        return recepcao.agendar().agendar(pedido(CONSULTA, ANA, em(SEXTA, hora, minuto), CARLA));
    }

    @Nested
    class Agendar {

        @Test
        void recepcaoAgendaParaOPacienteEPublicaOEvento() {
            Agendamento a = agendarSexta(9, 0);
            assertThat(a.status()).isEqualTo(StatusAgendamento.AGUARDANDO_CONFIRMACAO);
            assertThat(a.origem()).isEqualTo(OrigemAgendamento.RECEPCAO);
            assertThat(a.periodo()).isEqualTo(new Periodo(em(SEXTA, 9, 0), em(SEXTA, 9, 30)));
            assertThat(a.criadoPor()).isEqualTo("recepcao");
            assertThat(eventos).singleElement().isInstanceOf(AgendamentoCriado.class);
            assertThat(repositorio.historico).singleElement()
                    .satisfies(t -> assertThat(t.statusAnterior()).isEmpty());
        }

        @Test
        void pacienteAgendaSempreParaSiERespeitaAAntecedencia() {
            Agendamento a = carla.agendar().agendar(pedido(CONSULTA, ANA, em(SEXTA, 9, 0), RUI));
            assertThat(a.pacienteId()).isEqualTo(CARLA.id());
            assertThat(a.origem()).isEqualTo(OrigemAgendamento.PORTAL);
            Instant daquiA30min = Fixtures.AGORA.plus(Duration.ofMinutes(30));
            assertThatThrownBy(() -> carla.agendar().agendar(pedido(CONSULTA, ANA, daquiA30min, CARLA)))
                    .isInstanceOf(DadoInvalidoException.class).hasMessageContaining("antecedência");
        }

        @Test
        void pacienteSemCelularVerificadoOuSemCadastroNaoAgenda() {
            Casos rui = como(Fixtures.ator("rui", Papel.PACIENTE));
            assertThatThrownBy(() -> rui.agendar().agendar(pedido(CONSULTA, ANA, em(SEXTA, 9, 0), RUI)))
                    .hasMessageContaining("celular");
            Casos semCadastro = como(Fixtures.ator("novo", Papel.PACIENTE));
            assertThatThrownBy(() -> semCadastro.agendar().agendar(pedido(CONSULTA, ANA, em(SEXTA, 9, 0), RUI)))
                    .hasMessageContaining("Complete seu cadastro");
        }

        @Test
        void profissionalNaoAgendaERecepcaoPrecisaInformarPaciente() {
            assertThatThrownBy(() -> ana.agendar().agendar(pedido(CONSULTA, ANA, em(SEXTA, 9, 0), CARLA)))
                    .isInstanceOf(AcessoNegadoException.class);
            assertThatThrownBy(() -> recepcao.agendar().agendar(new AgendarConsulta.Pedido(CONSULTA, ANA,
                    em(SEXTA, 9, 0), Optional.empty(), Optional.empty()))).hasMessageContaining("paciente");
            assertThatThrownBy(() -> recepcao.agendar().agendar(new AgendarConsulta.Pedido(CONSULTA, ANA,
                    em(SEXTA, 9, 0), Optional.of(UUID.randomUUID()), Optional.empty())))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> como(Fixtures.ator("anonimo")).agendar().agendar(
                    pedido(CONSULTA, ANA, em(SEXTA, 9, 0), CARLA))).isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void recusaServicoQueOProfissionalNaoAtendeEHorarioForaDoExpediente() {
            assertThatThrownBy(() -> recepcao.agendar().agendar(pedido(UUID.randomUUID(), ANA, em(SEXTA, 9, 0), CARLA)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> recepcao.agendar().agendar(pedido(COM_SINAL, BRUNO, em(SEXTA, 9, 0), CARLA)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> agendarSexta(12, 0)).hasMessageContaining("não atende");
            catalogo.ausencias.add(new Periodo(em(SEXTA, 8, 0), em(SEXTA, 12, 0)));
            assertThatThrownBy(() -> agendarSexta(9, 0)).hasMessageContaining("não atende");
            assertThatThrownBy(() -> recepcao.agendar().agendar(pedido(CONSULTA, ANA,
                    Fixtures.AGORA.minusSeconds(60), CARLA))).hasMessageContaining("ainda não passou");
        }

        @Test
        void horarioOcupadoViraHorarioIndisponivel() {
            agendarSexta(9, 0);
            assertThatThrownBy(() -> agendarSexta(9, 15)).isInstanceOf(HorarioIndisponivelException.class);
            assertThat(agendarSexta(9, 30).status()).isEqualTo(StatusAgendamento.AGUARDANDO_CONFIRMACAO);
        }

        @Test
        void servicoComPagamentoAntecipadoReservaEHoldVencidoNaoBloqueia() {
            Agendamento reserva = recepcao.agendar().agendar(pedido(COM_SINAL, ANA, em(SEXTA, 10, 0), CARLA));
            assertThat(reserva.status()).isEqualTo(StatusAgendamento.RESERVADO);
            assertThat(reserva.valorAntecipado()).isEqualTo(new Dinheiro(5_000));

            Casos depoisDoPrazo = como(Fixtures.ator("recepcao", Papel.RECEPCAO),
                    Clock.fixed(Fixtures.AGORA.plus(Duration.ofMinutes(20)), ZoneOffset.UTC));
            Agendamento novo = depoisDoPrazo.agendar().agendar(pedido(CONSULTA, ANA, em(SEXTA, 10, 0), RUI));
            assertThat(novo.pacienteId()).isEqualTo(RUI.id());
            assertThat(repositorio.dados.get(reserva.id()).status()).isEqualTo(StatusAgendamento.EXPIRADO);
            assertThat(eventos).hasAtLeastOneElementOfType(ReservaExpirada.class);
        }

        @Test
        void comCobrancaAntecipadaDesligadaOServicoComSinalNaoSeguraOHorario() {
            ProvedorAtor ator = Fixtures.ator("recepcao", Papel.RECEPCAO);
            OperacoesAgenda operacoes = new OperacoesAgenda(repositorio, catalogo, regras, new OperacoesAgenda.Contexto(
                    Fixtures.CONTEXTO, ator, mock(RegistradorAuditoria.class), Fixtures.RELOGIO), eventos::add);
            AgendarConsulta semCobranca = new AgendarConsulta(repositorio, catalogo, pacientes,
                    new AcessoAgenda(ator, pacientes, catalogo), operacoes, false);
            Agendamento a = semCobranca.agendar(pedido(COM_SINAL, ANA, em(SEXTA, 10, 0), CARLA));
            assertThat(a.status()).isEqualTo(StatusAgendamento.AGUARDANDO_CONFIRMACAO);
            assertThat(a.valorAntecipado()).isEqualTo(Dinheiro.ZERO);
        }
    }

    @Nested
    class Alterar {

        @Test
        void remarcaComVersaoEPublicaVagaLiberada() {
            Agendamento a = agendarSexta(9, 0);
            eventos.clear();
            Agendamento remarcado = carla.gerir().remarcar(a.id(), em(SEXTA, 11, 0), a.versao());
            assertThat(remarcado.periodo().inicio()).isEqualTo(em(SEXTA, 11, 0));
            assertThat(remarcado.versao()).isEqualTo(1);
            assertThat(eventos).hasExactlyElementsOfTypes(AgendamentoRemarcado.class, VagaLiberada.class);
            assertThat(repositorio.historico).last().satisfies(t -> assertThat(t.periodoAnterior()).isPresent());

            assertThatThrownBy(() -> recepcao.gerir().remarcar(a.id(), em(SEXTA, 9, 0), 0))
                    .isInstanceOf(ConflitoException.class).hasMessageContaining("outra pessoa");
            assertThatThrownBy(() -> ana.gerir().remarcar(a.id(), em(SEXTA, 9, 0), 1))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void remarcarParaOProprioHorarioDeslocadoNaoColideConsigoMesmo() {
            Agendamento a = agendarSexta(9, 0);
            assertThat(recepcao.gerir().remarcar(a.id(), em(SEXTA, 9, 15), 0).periodo().fim())
                    .isEqualTo(em(SEXTA, 9, 45));
        }

        @Test
        void pacienteRespeitaPrazoDeAlteracaoMasRecepcaoNao() {
            Agendamento a = recepcao.agendar().agendar(pedido(CONSULTA, ANA,
                    LocalDate.of(2026, 10, 22).atTime(9, 0).atZone(FUSO).toInstant(), CARLA));
            assertThatThrownBy(() -> carla.gerir().cancelar(a.id(), Optional.empty()))
                    .hasMessageContaining("antecedência");
            assertThat(recepcao.gerir().cancelar(a.id(), Optional.of("Paciente ligou")).status())
                    .isEqualTo(StatusAgendamento.CANCELADO);
            assertThat(eventos).hasAtLeastOneElementOfType(AgendamentoCancelado.class)
                    .hasAtLeastOneElementOfType(VagaLiberada.class);
        }

        @Test
        void outroPacienteNaoVeNemAlteraEProfissionalNaoCancela() {
            Agendamento a = agendarSexta(9, 0);
            Casos rui = como(Fixtures.ator("rui", Papel.PACIENTE));
            assertThatThrownBy(() -> rui.gerir().cancelar(a.id(), Optional.empty()))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> rui.consultar().detalhe(a.id())).isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> ana.gerir().cancelar(a.id(), Optional.empty()))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void confirmarDuasVezesPublicaUmaVez() {
            Agendamento a = agendarSexta(9, 0);
            eventos.clear();
            carla.gerir().confirmar(a.id());
            assertThat(carla.gerir().confirmar(a.id()).status()).isEqualTo(StatusAgendamento.CONFIRMADO);
            assertThat(eventos).singleElement().isInstanceOf(AgendamentoConfirmado.class);
        }

        @Test
        void profissionalConduzOProprioAtendimentoNoDia() {
            Agendamento a = agendarSexta(9, 0);
            Casos anaNaSexta = como(Fixtures.ator("dra.ana", Papel.PROFISSIONAL),
                    Clock.fixed(em(SEXTA, 9, 5), ZoneOffset.UTC));
            assertThat(anaNaSexta.gerir().registrarPresenca(a.id()).status()).isEqualTo(StatusAgendamento.PRESENTE);
            assertThat(anaNaSexta.gerir().concluir(a.id()).status()).isEqualTo(StatusAgendamento.REALIZADO);
            assertThatThrownBy(() -> carla.gerir().registrarFalta(a.id())).isInstanceOf(AcessoNegadoException.class);

            Agendamento doBruno = recepcao.agendar().agendar(pedido(CONSULTA, BRUNO, em(SEXTA, 9, 0), RUI));
            assertThatThrownBy(() -> anaNaSexta.gerir().registrarFalta(doBruno.id()))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            Casos recepcaoNaSexta = como(Fixtures.ator("recepcao", Papel.RECEPCAO),
                    Clock.fixed(em(SEXTA, 9, 20), ZoneOffset.UTC));
            assertThat(recepcaoNaSexta.gerir().registrarFalta(doBruno.id()).status()).isEqualTo(StatusAgendamento.FALTA);
        }
    }

    @Nested
    class Consultar {

        @Test
        void pacienteVeHorariosAPartirDaAntecedenciaEEquipeDesdeAgora() {
            agendarSexta(9, 0);
            var paraPaciente = carla.disponibilidade().horarios(CONSULTA, ANA, LocalDate.of(2026, 10, 22), SEXTA);
            assertThat(paraPaciente.getFirst().periodo().inicio()).isEqualTo(em(LocalDate.of(2026, 10, 22), 9, 0));
            assertThat(paraPaciente).filteredOn(h -> !h.livre()).extracting(h -> h.periodo().inicio())
                    .containsExactly(em(SEXTA, 9, 0));

            var paraEquipe = recepcao.disponibilidade().horarios(CONSULTA, ANA, LocalDate.of(2026, 10, 22), SEXTA);
            assertThat(paraEquipe.getFirst().periodo().inicio()).isEqualTo(em(LocalDate.of(2026, 10, 22), 8, 0));
            assertThatThrownBy(() -> carla.disponibilidade().horarios(COM_SINAL, BRUNO, SEXTA, SEXTA))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        void gradeDoDiaTemUmaColunaPorProfissionalEOProfissionalSoVeASua() {
            agendarSexta(9, 0);
            recepcao.agendar().agendar(pedido(COM_SINAL, ANA, em(SEXTA, 10, 0), RUI));
            ConsultarAgenda.AgendaDoDia dia = recepcao.consultar().dia(SEXTA);
            assertThat(dia.colunas()).extracting(ConsultarAgenda.Coluna::profissional)
                    .containsExactlyInAnyOrder("Dra. Ana", "Dr. Bruno");
            assertThat(dia.resumo().agendados()).isEqualTo(2);
            assertThat(dia.resumo().aguardandoConfirmacao()).isEqualTo(2);
            ConsultarAgenda.Coluna colunaAna = dia.colunas().stream()
                    .filter(c -> c.profissionalId().equals(ANA)).findFirst().orElseThrow();
            assertThat(colunaAna.expediente()).hasSize(2);
            assertThat(colunaAna.agendamentos()).extracting(ConsultarAgenda.Item::paciente)
                    .containsExactly("Carla", "Rui");
            assertThat(colunaAna.livres()).hasSize(14);

            assertThat(ana.consultar().dia(SEXTA).colunas()).singleElement()
                    .satisfies(c -> assertThat(c.profissionalId()).isEqualTo(ANA));
            assertThatThrownBy(() -> carla.consultar().dia(SEXTA)).isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        void pacienteListaOsProprios() {
            Agendamento a = agendarSexta(9, 0);
            assertThat(carla.consultar().meus()).extracting(i -> i.agendamento().id()).containsExactly(a.id());
            assertThat(carla.consultar().detalhe(a.id()).servico()).isEqualTo("Consulta");
            assertThat(como(Fixtures.ator("novo", Papel.PACIENTE)).consultar().meus()).isEmpty();
        }

        @Test
        void jobExpiraReservasVencidasELiberaAVaga() {
            Agendamento reserva = recepcao.agendar().agendar(pedido(COM_SINAL, ANA, em(SEXTA, 10, 0), CARLA));
            assertThat(recepcao.expirar().executar()).isZero();
            Casos depois = como(Fixtures.ator("sistema"), Clock.fixed(Fixtures.AGORA.plus(Duration.ofMinutes(16)),
                    ZoneOffset.UTC));
            eventos.clear();
            assertThat(depois.expirar().executar()).isEqualTo(1);
            assertThat(repositorio.dados.get(reserva.id()).status()).isEqualTo(StatusAgendamento.EXPIRADO);
            assertThat(eventos).hasExactlyElementsOfTypes(ReservaExpirada.class, VagaLiberada.class);
        }
    }

    /** Catálogo com dois profissionais (seg–sex, 08–12 e 14–18) e dois serviços de 30 min. */
    static final class CatalogoFalso implements CatalogoAgenda {
        final List<Periodo> ausencias = new ArrayList<>();

        @Override
        public Optional<ServicoAgendavel> servico(UUID id) {
            if (id.equals(CONSULTA)) {
                return Optional.of(new ServicoAgendavel(CONSULTA, "Consulta", Duration.ofMinutes(30),
                        new Dinheiro(25_000), Dinheiro.ZERO, true));
            }
            return id.equals(COM_SINAL) ? Optional.of(new ServicoAgendavel(COM_SINAL, "Procedimento",
                    Duration.ofMinutes(30), new Dinheiro(40_000), new Dinheiro(5_000), true)) : Optional.empty();
        }

        @Override
        public Optional<ProfissionalAgendavel> profissional(UUID id) {
            return profissionaisAtivos().stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public List<ProfissionalAgendavel> profissionaisAtivos() {
            return List.of(new ProfissionalAgendavel(ANA, "Dra. Ana", Optional.of("dra.ana"), Set.of(CONSULTA, COM_SINAL),
                            true),
                    new ProfissionalAgendavel(BRUNO, "Dr. Bruno", Optional.of("dr.bruno"), Set.of(CONSULTA), true));
        }

        @Override
        public List<FaixaSemanal> expediente(UUID profissional) {
            List<FaixaSemanal> faixas = new ArrayList<>();
            for (DayOfWeek dia : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) {
                faixas.add(new FaixaSemanal(dia, LocalTime.of(8, 0), LocalTime.of(12, 0)));
                faixas.add(new FaixaSemanal(dia, LocalTime.of(14, 0), LocalTime.of(18, 0)));
            }
            return faixas;
        }

        @Override
        public List<AusenciaAgenda> ausencias(UUID profissional, Periodo janela) {
            return profissional.equals(ANA) ? ausencias.stream().filter(janela::sobrepoe)
                    .map(p -> new AusenciaAgenda(p, "BLOQUEIO")).toList() : List.of();
        }
    }

    /** Mesma regra da constraint do banco: dois agendamentos que ocupam horário não se sobrepõem. */
    static final class RepositorioEmMemoria implements AgendamentoRepositorio {
        final Map<UUID, Agendamento> dados = new LinkedHashMap<>();
        final List<Transicao> historico = new ArrayList<>();

        @Override
        public Optional<Agendamento> buscar(ClinicaId clinica, UUID id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public void travarAgendaDoProfissional(ClinicaId clinica, UUID profissional) {
            // um só thread nos testes unitários
        }

        @Override
        public void inserir(Agendamento a) {
            exigirLivre(a);
            dados.put(a.id(), a);
        }

        @Override
        public Agendamento atualizar(Agendamento a) {
            Agendamento atual = dados.get(a.id());
            if (atual == null || atual.versao() != a.versao()) {
                throw new ConflitoException("edicao-concorrente", "versão");
            }
            exigirLivre(a);
            Agendamento salvo = new Agendamento(a.id(), a.clinicaId(), a.profissionalId(), a.pacienteId(),
                    a.servicoId(), a.periodo(), a.status(), a.origem(), a.holdExpiraEm(), a.preco(),
                    a.valorAntecipado(), a.observacao(), a.cancelamento(), a.criadoPor(), a.versao() + 1);
            dados.put(a.id(), salvo);
            return salvo;
        }

        private void exigirLivre(Agendamento a) {
            if (a.status().ocupaHorario() && dados.values().stream().anyMatch(o -> !o.id().equals(a.id())
                    && o.status().ocupaHorario() && o.profissionalId().equals(a.profissionalId())
                    && o.periodo().sobrepoe(a.periodo()))) {
                throw new HorarioIndisponivelException();
            }
        }

        @Override
        public void registrarHistorico(ClinicaId clinica, Transicao transicao) {
            historico.add(transicao);
        }

        @Override
        public List<Agendamento> ocupando(ClinicaId clinica, UUID profissional, Periodo janela) {
            return dados.values().stream().filter(a -> a.profissionalId().equals(profissional)
                    && a.status().ocupaHorario() && a.periodo().sobrepoe(janela)).toList();
        }

        @Override
        public List<Agendamento> iniciandoEm(ClinicaId clinica, Optional<UUID> profissional, Periodo janela) {
            return dados.values().stream()
                    .filter(a -> profissional.map(a.profissionalId()::equals).orElse(true)
                            && janela.contem(a.periodo().inicio()))
                    .sorted(Comparator.comparing(a -> a.periodo().inicio())).toList();
        }

        @Override
        public List<Agendamento> doPaciente(ClinicaId clinica, UUID paciente, int limite) {
            return dados.values().stream().filter(a -> a.pacienteId().equals(paciente)).limit(limite).toList();
        }

        @Override
        public List<Agendamento> reservasVencidas(Instant agora, int limite) {
            return dados.values().stream().filter(a -> a.reservaVencida(agora)).limit(limite).toList();
        }
    }
}
