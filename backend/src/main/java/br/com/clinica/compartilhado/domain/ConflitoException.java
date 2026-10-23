package br.com.clinica.compartilhado.domain;

/** Estado atual impede a operação (duplicidade, edição concorrente...) — HTTP 409. */
public class ConflitoException extends ExcecaoNegocio {

    private static final long serialVersionUID = 1L;

    public ConflitoException(String codigo, String mensagem) {
        super(codigo, Categoria.CONFLITO, mensagem);
    }
}
