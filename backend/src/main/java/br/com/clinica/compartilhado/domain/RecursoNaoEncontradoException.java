package br.com.clinica.compartilhado.domain;

/**
 * Recurso inexistente OU fora do alcance do usuário. A mesma resposta para os dois casos evita
 * revelar a existência de registros de outra clínica/paciente (proteção contra IDOR).
 */
public class RecursoNaoEncontradoException extends ExcecaoNegocio {

    private static final long serialVersionUID = 1L;

    public RecursoNaoEncontradoException(String recurso) {
        super(recurso + "-nao-encontrado", Categoria.NAO_ENCONTRADO, "Registro não encontrado.");
    }
}
