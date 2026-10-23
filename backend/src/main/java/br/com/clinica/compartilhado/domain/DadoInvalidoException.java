package br.com.clinica.compartilhado.domain;

/** Valor que viola uma regra de formato/consistência do domínio (vira HTTP 422). */
public class DadoInvalidoException extends ExcecaoNegocio {

    private static final long serialVersionUID = 1L;

    public DadoInvalidoException(String codigo, String mensagem) {
        super(codigo, Categoria.REGRA_VIOLADA, mensagem);
    }
}
