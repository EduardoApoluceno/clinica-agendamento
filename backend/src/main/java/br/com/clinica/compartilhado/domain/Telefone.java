package br.com.clinica.compartilhado.domain;

import java.util.Objects;

/** Celular brasileiro no formato E.164 (+55 DDD 9XXXXXXXX), padrão usado pelo WhatsApp. */
public record Telefone(String e164) {

    public Telefone {
        Objects.requireNonNull(e164, "e164");
        if (!e164.matches("\\+55[1-9][0-9]9\\d{8}")) {
            throw new DadoInvalidoException("telefone-invalido", "Informe um celular válido com DDD.");
        }
    }

    /** Aceita "(69) 99999-1234", "69999991234" ou "+5569999991234". */
    public static Telefone de(String texto) {
        String digitos = texto == null ? "" : texto.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            digitos = "55" + digitos;
        }
        return new Telefone("+" + digitos);
    }

    /** Ex.: (69) *****-1234 */
    public String mascarado() {
        return "(" + e164.substring(3, 5) + ") *****-" + e164.substring(e164.length() - 4);
    }

    @Override
    public String toString() {
        return mascarado();
    }
}
