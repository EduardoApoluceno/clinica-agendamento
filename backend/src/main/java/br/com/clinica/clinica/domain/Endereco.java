package br.com.clinica.clinica.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.util.Optional;

/** Endereço postal brasileiro. */
public record Endereco(String logradouro, String numero, Optional<String> complemento, String bairro,
                       String cidade, String uf, String cep) {

    public Endereco {
        exigir(logradouro, "logradouro");
        exigir(numero, "numero");
        exigir(bairro, "bairro");
        exigir(cidade, "cidade");
        complemento = complemento == null ? Optional.empty() : complemento.filter(c -> !c.isBlank());
        if (uf == null || !uf.matches("[A-Z]{2}")) {
            throw new DadoInvalidoException("uf-invalida", "UF inválida.");
        }
        cep = cep == null ? "" : cep.replaceAll("\\D", "");
        if (!cep.matches("\\d{8}")) {
            throw new DadoInvalidoException("cep-invalido", "CEP inválido.");
        }
    }

    private static void exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new DadoInvalidoException(campo + "-obrigatorio", "Campo obrigatório: " + campo + ".");
        }
    }
}
