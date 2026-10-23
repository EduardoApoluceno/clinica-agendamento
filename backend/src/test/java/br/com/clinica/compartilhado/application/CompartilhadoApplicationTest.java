package br.com.clinica.compartilhado.application;

import br.com.clinica.compartilhado.domain.Papel;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompartilhadoApplicationTest {

    @Test
    void atorSistemaNaoTemPapeis() {
        Ator sistema = Ator.sistema();
        assertThat(sistema.sub()).isEqualTo(Ator.SISTEMA);
        assertThat(sistema.papeis()).isEmpty();
        assertThat(sistema.papelPrincipal()).isEmpty();
    }

    @Test
    void atorPapelPrincipalEhOMaisPrivilegiado() {
        Ator ator = new Ator("u1", "Ana", Set.of(Papel.PROFISSIONAL, Papel.ADMIN), Optional.of("10.0.0.1"),
                Optional.empty());
        assertThat(ator.possui(Papel.ADMIN)).isTrue();
        assertThat(ator.papelPrincipal()).contains(Papel.ADMIN);
    }

    @Test
    void pedidoDePaginaTemLimites() {
        assertThat(new Pagina.Pedido(-3, 5000)).isEqualTo(new Pagina.Pedido(0, Pagina.TAMANHO_MAXIMO));
        assertThat(new Pagina.Pedido(2, 10).deslocamento()).isEqualTo(20);
    }

    @Test
    void paginaMapeiaItensPreservandoMetadados() {
        Pagina<Integer> pagina = new Pagina<>(List.of(1, 2), 1, 2, 10);
        assertThat(pagina.mapear(i -> "n" + i)).isEqualTo(new Pagina<>(List.of("n1", "n2"), 1, 2, 10));
    }
}
