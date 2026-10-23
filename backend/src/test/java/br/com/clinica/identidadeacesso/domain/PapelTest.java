package br.com.clinica.identidadeacesso.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PapelTest {

    @Test
    void reconhecePapelIgnorandoCaixa() {
        assertThat(Papel.doNome("recepcao")).contains(Papel.RECEPCAO);
    }

    @Test
    void ignoraPapeisDesconhecidosDoKeycloak() {
        assertThat(Papel.doNome("default-roles-clinica")).isEmpty();
    }

    @Test
    void geraAutoridadeSpringEIdentificaEquipe() {
        assertThat(Papel.ADMIN.autoridade()).isEqualTo("ROLE_ADMIN");
        assertThat(Papel.PROFISSIONAL.equipe()).isTrue();
        assertThat(Papel.PACIENTE.equipe()).isFalse();
    }
}
