package br.com.clinica.compartilhado.domain;

import java.util.Locale;
import java.util.Objects;

/** Endereço de e-mail normalizado em minúsculas. */
public record Email(String valor) {

    private static final int TAMANHO_MAXIMO = 254;

    public Email {
        Objects.requireNonNull(valor, "valor");
        valor = valor.trim().toLowerCase(Locale.ROOT);
        if (valor.length() > TAMANHO_MAXIMO || !valor.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}")) {
            throw new DadoInvalidoException("email-invalido", "E-mail inválido.");
        }
    }

    public String mascarado() {
        int arroba = valor.indexOf('@');
        return valor.charAt(0) + "***" + valor.substring(arroba);
    }

    @Override
    public String toString() {
        return mascarado();
    }
}
