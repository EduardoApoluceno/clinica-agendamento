package br.com.clinica.agenda.domain;

import br.com.clinica.compartilhado.domain.Periodo;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Horários de um profissional para um serviço: candidatos alinhados ao início de cada faixa do
 * expediente, em passos da duração do serviço. Ausências removem o horário; agendamentos que ocupam
 * o horário o deixam visível como indisponível (a tela mostra riscado).
 */
public final class CalculadoraDisponibilidade {

    private static final int DIAS_MAXIMOS = 62;

    private CalculadoraDisponibilidade() {
    }

    public static List<Horario> horarios(Pedido pedido) {
        List<Horario> horarios = new ArrayList<>();
        for (LocalDate dia = pedido.de(); !dia.isAfter(pedido.ate()); dia = dia.plusDays(1)) {
            for (FaixaTrabalho faixa : pedido.faixas()) {
                if (faixa.dia() == dia.getDayOfWeek()) {
                    adicionarDaFaixa(pedido, dia, faixa, horarios);
                }
            }
        }
        horarios.sort((a, b) -> a.periodo().inicio().compareTo(b.periodo().inicio()));
        return List.copyOf(horarios);
    }

    /** O período cabe inteiro numa faixa do expediente e não toca nenhuma ausência? */
    public static boolean atendeNoPeriodo(Periodo periodo, List<FaixaTrabalho> faixas, List<Periodo> ausencias,
                                          ZoneId fuso) {
        LocalDate dia = periodo.inicio().atZone(fuso).toLocalDate();
        boolean dentro = faixas.stream().filter(f -> f.dia() == dia.getDayOfWeek())
                .map(f -> f.periodoEm(dia, fuso))
                .anyMatch(f -> !periodo.inicio().isBefore(f.inicio()) && !periodo.fim().isAfter(f.fim()));
        return dentro && ausencias.stream().noneMatch(periodo::sobrepoe);
    }

    private static void adicionarDaFaixa(Pedido pedido, LocalDate dia, FaixaTrabalho faixa, List<Horario> destino) {
        Periodo limites = faixa.periodoEm(dia, pedido.fuso());
        for (Instant inicio = limites.inicio(); !inicio.plus(pedido.duracao()).isAfter(limites.fim());
             inicio = inicio.plus(pedido.duracao())) {
            Periodo candidato = new Periodo(inicio, inicio.plus(pedido.duracao()));
            boolean naJanela = !inicio.isBefore(pedido.naoAntesDe()) && !inicio.isAfter(pedido.naoDepoisDe());
            if (naJanela && pedido.ausencias().stream().noneMatch(candidato::sobrepoe)) {
                destino.add(new Horario(candidato, pedido.ocupados().stream().noneMatch(candidato::sobrepoe)));
            }
        }
    }

    /**
     * @param naoAntesDe horários que começam antes disso não aparecem (passado, antecedência mínima)
     * @param naoDepoisDe horizonte de agendamento
     */
    public record Pedido(LocalDate de, LocalDate ate, ZoneId fuso, List<FaixaTrabalho> faixas, List<Periodo> ausencias,
                         List<Periodo> ocupados, Duration duracao, Instant naoAntesDe, Instant naoDepoisDe) {
        public Pedido {
            Objects.requireNonNull(de, "de");
            Objects.requireNonNull(ate, "ate");
            Objects.requireNonNull(fuso, "fuso");
            Objects.requireNonNull(naoAntesDe, "naoAntesDe");
            Objects.requireNonNull(naoDepoisDe, "naoDepoisDe");
            if (duracao == null || !duracao.isPositive()) {
                throw new IllegalArgumentException("duração deve ser positiva");
            }
            if (ate.isBefore(de) || de.plusDays(DIAS_MAXIMOS).isBefore(ate)) {
                throw new IllegalArgumentException("intervalo de datas inválido");
            }
            faixas = List.copyOf(faixas);
            ausencias = List.copyOf(ausencias);
            ocupados = List.copyOf(ocupados);
        }
    }

    /** Faixa do expediente semanal (horário local da clínica). */
    public record FaixaTrabalho(DayOfWeek dia, LocalTime inicio, LocalTime fim) {
        Periodo periodoEm(LocalDate data, ZoneId fuso) {
            return new Periodo(data.atTime(inicio).atZone(fuso).toInstant(), data.atTime(fim).atZone(fuso).toInstant());
        }
    }

    public record Horario(Periodo periodo, boolean livre) { }
}
