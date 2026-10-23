package br.com.clinica.clinica.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.util.Objects;

/** CNPJ numérico válido (dígitos verificadores conferidos). */
public record Cnpj(String digitos) {

    private static final int[] PESOS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        Objects.requireNonNull(digitos, "digitos");
        if (!valido(digitos)) {
            throw new DadoInvalidoException("cnpj-invalido", "CNPJ inválido.");
        }
    }

    public static Cnpj de(String texto) {
        return new Cnpj(texto == null ? "" : texto.replaceAll("\\D", ""));
    }

    static boolean valido(String d) {
        if (!d.matches("\\d{14}") || d.chars().distinct().count() == 1) {
            return false;
        }
        return digito(d, PESOS_1) == d.charAt(12) - '0' && digito(d, PESOS_2) == d.charAt(13) - '0';
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
