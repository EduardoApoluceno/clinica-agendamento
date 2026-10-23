package br.com.clinica.compartilhado.application;

import java.util.List;
import java.util.function.Function;

/** Página de resultados (numeração a partir de 0). */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {

    public static final int TAMANHO_MAXIMO = 100;

    public Pagina {
        itens = List.copyOf(itens);
    }

    public <R> Pagina<R> mapear(Function<T, R> conversor) {
        return new Pagina<>(itens.stream().map(conversor).toList(), pagina, tamanho, total);
    }

    /** Pedido de página com limites saneados (evita páginas gigantes vindas do cliente). */
    public record Pedido(int pagina, int tamanho) {
        public Pedido {
            pagina = Math.max(pagina, 0);
            tamanho = Math.clamp(tamanho, 1, TAMANHO_MAXIMO);
        }

        public long deslocamento() {
            return (long) pagina * tamanho;
        }
    }
}
