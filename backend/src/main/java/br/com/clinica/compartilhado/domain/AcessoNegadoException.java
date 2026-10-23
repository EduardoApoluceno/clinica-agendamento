package br.com.clinica.compartilhado.domain;

/** O usuário vê o recurso, mas não pode executar esta operação nele — HTTP 403. */
public class AcessoNegadoException extends ExcecaoNegocio {

    private static final long serialVersionUID = 1L;

    public AcessoNegadoException(String codigo, String mensagem) {
        super(codigo, Categoria.ACESSO_NEGADO, mensagem);
    }
}
