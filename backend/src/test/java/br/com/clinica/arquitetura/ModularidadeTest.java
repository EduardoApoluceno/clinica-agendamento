package br.com.clinica.arquitetura;

import br.com.clinica.ClinicaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

class ModularidadeTest {

    private final ApplicationModules modulos = ApplicationModules.of(ClinicaApplication.class);

    @Test
    void modulosRespeitamFronteiras() {
        modulos.verify();
    }

    @Test
    void todosOsModulosPrevistosExistem() {
        assertThat(modulos.stream().map(m -> m.getDisplayName()))
                .contains("identidade-acesso", "clinica", "profissionais", "pacientes", "agenda",
                        "prontuario", "notificacoes", "pagamentos", "fiscal", "lista-espera",
                        "integracoes-calendario", "assistente-ia", "relatorios", "auditoria");
    }

    @Test
    void geraDocumentacaoDosModulos() {
        new Documenter(modulos).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
