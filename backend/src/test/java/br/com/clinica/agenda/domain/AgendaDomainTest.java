package br.com.clinica.agenda.domain;

import br.com.clinica.Fixtures;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.FaixaTrabalho;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.Horario;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgendaDomainTest {

    static final ZoneId FUSO = ZoneId.of("America/Porto_Velho");
    /** Quinta-feira, 22/10/2026, 08:00 em Porto Velho. */
    static final Instant AGORA = Fixtures.AGORA;
    static final LocalDate SEXTA = LocalDate.of(2026, 10, 23);

    static Instant em(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(FUSO).toInstant();
    }

    static Agendamento novo(long antecipadoCentavos) {
        Periodo periodo = new Periodo(em(SEXTA, 9, 0), em(SEXTA, 9, 30));
        return Agendamento.novo(new Agendamento.Dados(Fixtures.CLINICA, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), periodo, OrigemAgendamento.PORTAL, new Dinheiro(20_000),
                new Dinheiro(antecipadoCentavos), Optional.of("  primeira consulta "), "paciente"),
                AGORA, Duration.ofMinutes(15));
    }

    @Nested
    class MaquinaDeEstados {

        @Test
        void semPagamentoAguardaConfirmacaoEComPagamentoReservaComPrazo() {
            Agendamento simples = novo(0);
            assertThat(simples.status()).isEqualTo(StatusAgendamento.AGUARDANDO_CONFIRMACAO);
            assertThat(simples.holdExpiraEm()).isEmpty();
            assertThat(simples.observacao()).contains("primeira consulta");

            Agendamento comSinal = novo(5_000);
            assertThat(comSinal.status()).isEqualTo(StatusAgendamento.RESERVADO);
            assertThat(comSinal.holdExpiraEm()).contains(AGORA.plus(Duration.ofMinutes(15)));
        }

        @Test
        void confirmaUmaVezEConfirmarDeNovoNaoMudaNada() {
            Agendamento confirmado = novo(0).confirmado();
            assertThat(confirmado.status()).isEqualTo(StatusAgendamento.CONFIRMADO);
            assertThat(confirmado.confirmado()).isSameAs(confirmado);
            assertThatThrownBy(() -> novo(5_000).confirmado()).isInstanceOf(ConflitoException.class);
        }

        @Test
        void pagamentoConfirmadoEfetivaAReserva() {
            Agendamento pago = novo(5_000).pagamentoConfirmado();
            assertThat(pago.status()).isEqualTo(StatusAgendamento.CONFIRMADO);
            assertThat(pago.holdExpiraEm()).isEmpty();
            assertThatThrownBy(() -> novo(0).pagamentoConfirmado()).isInstanceOf(ConflitoException.class);
        }

        @Test
        void remarcaECancelaSoEnquantoPendente() {
            Agendamento a = novo(0);
            Periodo outro = new Periodo(em(SEXTA, 10, 0), em(SEXTA, 10, 30));
            assertThat(a.remarcadoPara(outro).periodo()).isEqualTo(outro);

            Agendamento cancelado = a.cancelado(AGORA, Optional.of(" imprevisto "));
            assertThat(cancelado.status()).isEqualTo(StatusAgendamento.CANCELADO);
            assertThat(cancelado.cancelamento()).hasValueSatisfying(c -> {
                assertThat(c.em()).isEqualTo(AGORA);
                assertThat(c.motivo()).contains("imprevisto");
            });
            assertThatThrownBy(() -> cancelado.remarcadoPara(outro)).isInstanceOf(ConflitoException.class);
            assertThatThrownBy(() -> cancelado.cancelado(AGORA, Optional.empty())).isInstanceOf(ConflitoException.class);
        }

        @Test
        void presencaSoNoDiaEDepoisConclusao() {
            Agendamento a = novo(0).confirmado();
            assertThatThrownBy(() -> a.presente(AGORA, FUSO)).isInstanceOf(ConflitoException.class);
            Agendamento presente = a.presente(em(SEXTA, 8, 50), FUSO);
            assertThat(presente.status()).isEqualTo(StatusAgendamento.PRESENTE);
            assertThat(presente.realizado().status()).isEqualTo(StatusAgendamento.REALIZADO);
            assertThatThrownBy(a::realizado).isInstanceOf(ConflitoException.class);
        }

        @Test
        void faltaSoDepoisDoHorario() {
            Agendamento a = novo(0);
            assertThatThrownBy(() -> a.falta(em(SEXTA, 8, 59))).isInstanceOf(ConflitoException.class);
            assertThat(a.falta(em(SEXTA, 9, 15)).status()).isEqualTo(StatusAgendamento.FALTA);
            assertThatThrownBy(() -> a.cancelado(AGORA, Optional.empty()).falta(em(SEXTA, 10, 0)))
                    .isInstanceOf(ConflitoException.class);
        }

        @Test
        void reservaExpiraSoDepoisDoPrazo() {
            Agendamento reserva = novo(5_000);
            Instant prazo = reserva.holdExpiraEm().orElseThrow();
            assertThat(reserva.reservaVencida(prazo.minusSeconds(1))).isFalse();
            assertThatThrownBy(() -> reserva.expirado(prazo.minusSeconds(1))).isInstanceOf(ConflitoException.class);
            assertThat(reserva.expirado(prazo).status()).isEqualTo(StatusAgendamento.EXPIRADO);
            assertThat(novo(0).reservaVencida(prazo)).isFalse();
        }

        @Test
        void estadosQueOcupamHorarioSaoOsDaConstraint() {
            assertThat(StatusAgendamento.values()).filteredOn(StatusAgendamento::ocupaHorario)
                    .containsExactlyInAnyOrder(StatusAgendamento.RESERVADO, StatusAgendamento.AGUARDANDO_CONFIRMACAO,
                            StatusAgendamento.CONFIRMADO, StatusAgendamento.PRESENTE, StatusAgendamento.REALIZADO,
                            StatusAgendamento.FALTA);
            assertThat(StatusAgendamento.CANCELADO.pendente()).isFalse();
        }

        @Test
        void textosLongosSaoRecusados() {
            Agendamento a = novo(0);
            assertThatThrownBy(() -> a.cancelado(AGORA, Optional.of("x".repeat(201))))
                    .isInstanceOf(DadoInvalidoException.class);
        }
    }

    @Nested
    class Antecedencia {
        private final PoliticaAntecedencia politica = new PoliticaAntecedencia(Duration.ofHours(1), Duration.ofHours(2),
                Duration.ofDays(30));

        @Test
        void novoHorarioRespeitaAntecedenciaEHorizonte() {
            politica.exigirNovoHorario(AGORA, AGORA.plus(Duration.ofHours(1)));
            assertThatThrownBy(() -> politica.exigirNovoHorario(AGORA, AGORA.minusSeconds(60)))
                    .hasMessageContaining("ainda não passou");
            assertThatThrownBy(() -> politica.exigirNovoHorario(AGORA, AGORA.plus(Duration.ofMinutes(59))))
                    .hasMessageContaining("1 hora");
            assertThatThrownBy(() -> politica.exigirNovoHorario(AGORA, AGORA.plus(Duration.ofDays(31))))
                    .hasMessageContaining("30 dias");
        }

        @Test
        void alteracaoExigeAntecedenciaDoHorarioAtual() {
            politica.exigirAlteracao(AGORA, AGORA.plus(Duration.ofHours(2)));
            assertThatThrownBy(() -> politica.exigirAlteracao(AGORA, AGORA.plus(Duration.ofMinutes(90))))
                    .isInstanceOf(DadoInvalidoException.class).hasMessageContaining("2 horas");
            assertThatThrownBy(() -> new PoliticaAntecedencia(Duration.ofMinutes(45), Duration.ofMinutes(45),
                    Duration.ofDays(1)).exigirAlteracao(AGORA, AGORA)).hasMessageContaining("45 min");
        }
    }

    @Nested
    class Disponibilidade {
        /** Sexta: 08:00–10:00 e 14:00–15:00 (intervalo de almoço = lacuna). */
        private final List<FaixaTrabalho> faixas = List.of(
                new FaixaTrabalho(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)),
                new FaixaTrabalho(DayOfWeek.FRIDAY, LocalTime.of(14, 0), LocalTime.of(15, 0)));

        private List<Horario> horarios(List<Periodo> ausencias, List<Periodo> ocupados, Instant naoAntesDe) {
            return CalculadoraDisponibilidade.horarios(new CalculadoraDisponibilidade.Pedido(SEXTA, SEXTA.plusDays(1),
                    FUSO, faixas, ausencias, ocupados, Duration.ofMinutes(30), naoAntesDe, AGORA.plus(Duration.ofDays(60))));
        }

        @Test
        void geraHorariosAlinhadosAoExpedienteEmPassosDaDuracao() {
            List<Horario> h = horarios(List.of(), List.of(), AGORA);
            assertThat(h).extracting(x -> x.periodo().inicio()).containsExactly(em(SEXTA, 8, 0), em(SEXTA, 8, 30),
                    em(SEXTA, 9, 0), em(SEXTA, 9, 30), em(SEXTA, 14, 0), em(SEXTA, 14, 30));
            assertThat(h).allMatch(Horario::livre);
        }

        @Test
        void ocupadosAparecemIndisponiveisEAusenciasSomem() {
            Periodo ocupado = new Periodo(em(SEXTA, 8, 30), em(SEXTA, 9, 0));
            Periodo ausencia = new Periodo(em(SEXTA, 14, 0), em(SEXTA, 18, 0));
            List<Horario> h = horarios(List.of(ausencia), List.of(ocupado), AGORA);
            assertThat(h).hasSize(4);
            assertThat(h).filteredOn(x -> !x.livre()).extracting(Horario::periodo).containsExactly(ocupado);
        }

        @Test
        void naoMostraHorariosAntesDoLimite() {
            assertThat(horarios(List.of(), List.of(), em(SEXTA, 9, 0))).extracting(x -> x.periodo().inicio())
                    .startsWith(em(SEXTA, 9, 0));
        }

        @Test
        void conferePeriodoDentroDoExpediente() {
            List<Periodo> semAusencia = List.of();
            assertThat(CalculadoraDisponibilidade.atendeNoPeriodo(new Periodo(em(SEXTA, 9, 15), em(SEXTA, 9, 45)),
                    faixas, semAusencia, FUSO)).isTrue();
            assertThat(CalculadoraDisponibilidade.atendeNoPeriodo(new Periodo(em(SEXTA, 9, 45), em(SEXTA, 10, 15)),
                    faixas, semAusencia, FUSO)).isFalse();
            assertThat(CalculadoraDisponibilidade.atendeNoPeriodo(new Periodo(em(SEXTA, 9, 0), em(SEXTA, 9, 30)),
                    faixas, List.of(new Periodo(em(SEXTA, 9, 20), em(SEXTA, 9, 40))), FUSO)).isFalse();
        }

        @Test
        void recusaPedidosInvalidos() {
            assertThatThrownBy(() -> new CalculadoraDisponibilidade.Pedido(SEXTA, SEXTA.minusDays(1), FUSO, faixas,
                    List.of(), List.of(), Duration.ofMinutes(30), AGORA, AGORA)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CalculadoraDisponibilidade.Pedido(SEXTA, SEXTA, FUSO, faixas,
                    List.of(), List.of(), Duration.ZERO, AGORA, AGORA)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
