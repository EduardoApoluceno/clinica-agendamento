package br.com.clinica.compartilhado.domain;

import java.util.Objects;

/** CPF válido (dígitos verificadores conferidos), armazenado só com dígitos. */
public record Cpf(String digitos) {

    public Cpf {
        Objects.requireNonNull(digitos, "digitos");
        if (!valido(digitos)) {
            throw new DadoInvalidoException("cpf-invalido", "CPF inválido.");
        }
    }

    /** Aceita com ou sem pontuação. */
    public static Cpf de(String texto) {
        return new Cpf(texto == null ? "" : texto.replaceAll("\\D", ""));
    }

    public String formatado() {
        return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "." + digitos.substring(6, 9)
                + "-" + digitos.substring(9);
    }

    /** Ex.: ***.456.789-** — para exibir sem expor o número completo. */
    public String mascarado() {
        return "***." + digitos.substring(3, 6) + "." + digitos.substring(6, 9) + "-**";
    }

    @Override
    public String toString() {
        return mascarado();
    }

    static boolean valido(String d) {
        if (!d.matches("\\d{11}") || d.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(d, 9) == d.charAt(9) - '0' && digitoVerificador(d, 10) == d.charAt(10) - '0';
    }

    private static int digitoVerificador(String d, int posicoes) {
        int soma = 0;
        for (int i = 0; i < posicoes; i++) {
            soma += (d.charAt(i) - '0') * (posicoes + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
