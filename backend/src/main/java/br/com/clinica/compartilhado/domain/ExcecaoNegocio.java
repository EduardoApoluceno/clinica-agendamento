package br.com.clinica.compartilhado.domain;

/**
 * Base das exceções de regra de negócio. O domínio informa apenas a categoria; a tradução para
 * HTTP (Problem Details) acontece no adaptador web.
 */
public abstract class ExcecaoNegocio extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Categoria semântica do erro, independente de protocolo. */
    public enum Categoria { NAO_ENCONTRADO, CONFLITO, REGRA_VIOLADA, ACESSO_NEGADO }

    private final String codigo;
    private final Categoria categoria;

    protected ExcecaoNegocio(String codigo, Categoria categoria, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
        this.categoria = categoria;
    }

    /** Identificador estável do problema, ex.: {@code horario-indisponivel}. */
    public String codigo() {
        return codigo;
    }

    public Categoria categoria() {
        return categoria;
    }
}
