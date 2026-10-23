package br.com.clinica.clinica.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * Dados cadastrais da clínica.
 *
 * @param versao controle de concorrência otimista
 */
public record Clinica(ClinicaId id, String nome, Cnpj cnpj, ZoneId fusoHorario, Endereco endereco,
                      Optional<String> telefone, long versao) {

    private static final int TAMANHO_MAXIMO_NOME = 160;

    public Clinica {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cnpj, "cnpj");
        Objects.requireNonNull(fusoHorario, "fusoHorario");
        Objects.requireNonNull(endereco, "endereco");
        if (nome == null || nome.isBlank() || nome.length() > TAMANHO_MAXIMO_NOME) {
            throw new DadoInvalidoException("nome-invalido", "Informe o nome da clínica (até 160 caracteres).");
        }
        nome = nome.strip();
        telefone = telefone == null ? Optional.empty() : telefone.filter(t -> !t.isBlank());
    }

    public Clinica comDados(String novoNome, Cnpj novoCnpj, Endereco novoEndereco, Optional<String> novoTelefone) {
        return new Clinica(id, novoNome, novoCnpj, fusoHorario, novoEndereco, novoTelefone, versao);
    }
}
