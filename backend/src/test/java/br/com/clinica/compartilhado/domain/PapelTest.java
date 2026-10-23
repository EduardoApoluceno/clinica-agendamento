package br.com.clinica.compartilhado.domain;

import java.util.List;
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

    @Test
    void equipeNaoAcumulaPapelPacientePadrao() {
        assertThat(Papel.efetivos(List.of(Papel.RECEPCAO, Papel.PACIENTE))).containsExactly(Papel.RECEPCAO);
        assertThat(Papel.efetivos(List.of(Papel.PACIENTE))).containsExactly(Papel.PACIENTE);
        assertThat(Papel.efetivos(List.of())).isEmpty();
    }

    @Test
    void matrizDePermissoes() {
        assertThat(Papel.ADMIN.permissoes()).contains(Permissao.AUDITORIA_LER, Permissao.CLINICA_ADMINISTRAR);
        assertThat(Papel.RECEPCAO.permissoes()).contains(Permissao.PACIENTES_ESCREVER)
                .doesNotContain(Permissao.AUDITORIA_LER, Permissao.PROFISSIONAIS_ADMINISTRAR);
        assertThat(Papel.PROFISSIONAL.permissoes()).contains(Permissao.PACIENTES_LER)
                .doesNotContain(Permissao.PACIENTES_ESCREVER);
        assertThat(Papel.PACIENTE.permissoes()).containsExactly(Permissao.CADASTRO_PROPRIO);
        assertThat(Permissao.PACIENTES_LER.autoridade()).isEqualTo("pacientes:ler");
    }
}
