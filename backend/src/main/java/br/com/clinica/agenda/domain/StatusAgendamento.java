package br.com.clinica.agenda.domain;

import java.util.EnumSet;
import java.util.Set;

/** Estados do agendamento (máquina de estados da proposta da Fase 0, seção 4). */
public enum StatusAgendamento {
    /** Horário segurado enquanto o pagamento antecipado não chega (expira por job). */
    RESERVADO,
    AGUARDANDO_CONFIRMACAO,
    CONFIRMADO,
    PRESENTE,
    REALIZADO,
    FALTA,
    CANCELADO,
    EXPIRADO;

    private static final Set<StatusAgendamento> OCUPAM = EnumSet.of(RESERVADO, AGUARDANDO_CONFIRMACAO, CONFIRMADO,
            PRESENTE, REALIZADO, FALTA);
    private static final Set<StatusAgendamento> FUTUROS = EnumSet.of(RESERVADO, AGUARDANDO_CONFIRMACAO, CONFIRMADO);

    /** Mesmo conjunto do WHERE da constraint {@code agendamento_sem_sobreposicao}. */
    public boolean ocupaHorario() {
        return OCUPAM.contains(this);
    }

    /** Ainda não aconteceu: pode ser remarcado ou cancelado. */
    public boolean pendente() {
        return FUTUROS.contains(this);
    }
}
