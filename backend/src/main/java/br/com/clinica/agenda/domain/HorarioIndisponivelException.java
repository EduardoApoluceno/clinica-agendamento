package br.com.clinica.agenda.domain;

import br.com.clinica.compartilhado.domain.ConflitoException;

/** O horário já está ocupado (violação da constraint de sobreposição ou checagem prévia) — HTTP 409. */
public class HorarioIndisponivelException extends ConflitoException {

    private static final long serialVersionUID = 1L;

    public HorarioIndisponivelException() {
        super("horario-indisponivel", "Este horário acabou de ser ocupado. Escolha outro.");
    }
}
