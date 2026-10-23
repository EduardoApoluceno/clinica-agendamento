package br.com.clinica.clinica.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.time.Duration;
import java.util.List;

/**
 * Regras configuráveis da agenda da clínica.
 *
 * @param lembretes antecedências dos lembretes automáticos (ex.: 24h e 2h)
 */
public record ConfiguracaoAgenda(Duration antecedenciaMinimaCancelamento, Duration antecedenciaMinimaAgendamento,
                                 Duration horizonteAgendamento, int minutosHoldPagamento, List<Duration> lembretes,
                                 Duration prazoAceiteListaEspera, boolean smsFallbackAtivo,
                                 boolean exigeJustificativaProntuarioSemVinculo) {

    private static final int HOLD_MINIMO = 5;
    private static final int HOLD_MAXIMO = 60;
    private static final int MAXIMO_LEMBRETES = 5;
    private static final Duration HORIZONTE_MAXIMO = Duration.ofDays(365);

    public ConfiguracaoAgenda {
        naoNegativo(antecedenciaMinimaCancelamento, "antecedencia-cancelamento");
        naoNegativo(antecedenciaMinimaAgendamento, "antecedencia-agendamento");
        positivo(prazoAceiteListaEspera, "prazo-lista-espera");
        positivo(horizonteAgendamento, "horizonte");
        if (horizonteAgendamento.compareTo(HORIZONTE_MAXIMO) > 0) {
            throw new DadoInvalidoException("horizonte-invalido", "O horizonte máximo é de 365 dias.");
        }
        if (minutosHoldPagamento < HOLD_MINIMO || minutosHoldPagamento > HOLD_MAXIMO) {
            throw new DadoInvalidoException("hold-invalido", "A reserva para pagamento deve durar de 5 a 60 minutos.");
        }
        lembretes = List.copyOf(lembretes);
        if (lembretes.size() > MAXIMO_LEMBRETES || lembretes.stream().anyMatch(l -> !l.isPositive())) {
            throw new DadoInvalidoException("lembretes-invalidos", "Use até 5 lembretes com antecedência positiva.");
        }
    }

    public static ConfiguracaoAgenda padrao() {
        return new ConfiguracaoAgenda(Duration.ofHours(2), Duration.ofHours(1), Duration.ofDays(60), 15,
                List.of(Duration.ofHours(24), Duration.ofHours(2)), Duration.ofMinutes(30), false, true);
    }

    private static void naoNegativo(Duration valor, String campo) {
        if (valor == null || valor.isNegative()) {
            throw new DadoInvalidoException(campo + "-invalida", "Valor inválido: " + campo + ".");
        }
    }

    private static void positivo(Duration valor, String campo) {
        if (valor == null || !valor.isPositive()) {
            throw new DadoInvalidoException(campo + "-invalido", "Valor inválido: " + campo + ".");
        }
    }
}
