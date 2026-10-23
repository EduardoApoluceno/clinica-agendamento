package br.com.clinica.profissionais.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Expediente semanal do profissional como faixas de trabalho por dia. Intervalos (almoço etc.)
 * são as lacunas entre faixas do mesmo dia. Horários no fuso da clínica.
 */
public record Expediente(List<Faixa> faixas) {

    private static final int MAXIMO_FAIXAS = 28;

    public Expediente {
        faixas = faixas.stream().sorted(Comparator.comparing(Faixa::dia).thenComparing(Faixa::inicio)).toList();
        if (faixas.size() > MAXIMO_FAIXAS) {
            throw new DadoInvalidoException("expediente-invalido", "Faixas de expediente demais.");
        }
        validarSemSobreposicao(faixas);
    }

    public Map<DayOfWeek, List<Faixa>> porDia() {
        return faixas.stream().collect(Collectors.groupingBy(Faixa::dia));
    }

    private static void validarSemSobreposicao(List<Faixa> ordenadas) {
        for (int i = 1; i < ordenadas.size(); i++) {
            Faixa anterior = ordenadas.get(i - 1);
            Faixa atual = ordenadas.get(i);
            if (anterior.dia() == atual.dia() && atual.inicio().isBefore(anterior.fim())) {
                throw new DadoInvalidoException("expediente-sobreposto",
                        "Há faixas de expediente sobrepostas em " + atual.dia() + ".");
            }
        }
    }

    /** Faixa contínua de atendimento em um dia da semana. */
    public record Faixa(DayOfWeek dia, LocalTime inicio, LocalTime fim) {
        public Faixa {
            Objects.requireNonNull(dia, "dia");
            Objects.requireNonNull(inicio, "inicio");
            Objects.requireNonNull(fim, "fim");
            if (!fim.isAfter(inicio)) {
                throw new DadoInvalidoException("faixa-invalida", "O fim da faixa deve ser após o início.");
            }
        }
    }
}
