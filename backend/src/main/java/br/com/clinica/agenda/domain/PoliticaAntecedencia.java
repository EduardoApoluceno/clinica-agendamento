package br.com.clinica.agenda.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Prazos configurados pela clínica para o paciente. A equipe não está sujeita a eles (encaixes,
 * remarcações de última hora), mas ninguém agenda no passado.
 *
 * @param minimaAgendamento antecedência mínima para marcar um horário novo
 * @param minimaAlteracao antecedência mínima para remarcar ou cancelar (sem penalidade)
 * @param horizonte até quando à frente o paciente pode marcar
 */
public record PoliticaAntecedencia(Duration minimaAgendamento, Duration minimaAlteracao, Duration horizonte) {

    public PoliticaAntecedencia {
        Objects.requireNonNull(minimaAgendamento, "minimaAgendamento");
        Objects.requireNonNull(minimaAlteracao, "minimaAlteracao");
        Objects.requireNonNull(horizonte, "horizonte");
    }

    public static void exigirFuturo(Instant agora, Instant inicio) {
        if (!inicio.isAfter(agora)) {
            throw new DadoInvalidoException("horario-passado", "Escolha um horário que ainda não passou.");
        }
    }

    /** Primeiro instante que o paciente pode marcar. */
    public Instant inicioPermitido(Instant agora) {
        return agora.plus(minimaAgendamento);
    }

    public Instant fimPermitido(Instant agora) {
        return agora.plus(horizonte);
    }

    public void exigirNovoHorario(Instant agora, Instant inicio) {
        exigirFuturo(agora, inicio);
        if (inicio.isBefore(inicioPermitido(agora))) {
            throw new DadoInvalidoException("antecedencia-insuficiente",
                    "Este horário exige pelo menos " + horas(minimaAgendamento) + " de antecedência.");
        }
        if (inicio.isAfter(fimPermitido(agora))) {
            throw new DadoInvalidoException("fora-do-horizonte",
                    "A agenda está aberta só para os próximos " + horizonte.toDays() + " dias.");
        }
    }

    /** Remarcar ou cancelar: o horário atual precisa estar a pelo menos {@code minimaAlteracao}. */
    public void exigirAlteracao(Instant agora, Instant inicioAtual) {
        if (agora.plus(minimaAlteracao).isAfter(inicioAtual)) {
            throw new DadoInvalidoException("prazo-alteracao-encerrado",
                    "Remarcações e cancelamentos precisam de " + horas(minimaAlteracao)
                            + " de antecedência. Fale com a recepção.");
        }
    }

    private static String horas(Duration duracao) {
        long minutos = duracao.toMinutes();
        if (minutos % 60 != 0) {
            return minutos + " min";
        }
        long horas = minutos / 60;
        return horas == 1 ? "1 hora" : horas + " horas";
    }
}
