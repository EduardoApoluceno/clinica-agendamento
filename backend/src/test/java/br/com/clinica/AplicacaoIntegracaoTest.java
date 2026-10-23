package br.com.clinica;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.auditoria.application.VerificarCadeiaAuditoria;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfraestruturaContainers.class)
class AplicacaoIntegracaoTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RegistradorAuditoria auditoria;

    @Autowired
    VerificarCadeiaAuditoria verificarCadeia;

    @Autowired
    TransactionTemplate transacao;

    @Test
    void healthEstaPublico() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void apiSemSessaoRetorna401EmVezDeRedirecionar() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void navegacaoSemSessaoRedirecionaParaKeycloak() throws Exception {
        mvc.perform(get("/qualquer-rota").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/oauth2/authorization/keycloak")));
    }

    @Test
    void meDevolvePapeisEPermissoesDaSessao() throws Exception {
        mvc.perform(get("/api/v1/me").with(oidcLogin()
                        .idToken(t -> t.subject("u-123").claims(c -> c.putAll(Map.of(
                                "name", "Recepção Demo", "roles", List.of("recepcao")))))
                        .authorities(new SimpleGrantedAuthority("ROLE_RECEPCAO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u-123"))
                .andExpect(jsonPath("$.nome").value("Recepção Demo"))
                .andExpect(jsonPath("$.papeis[0]").value("RECEPCAO"))
                .andExpect(jsonPath("$.equipe").value(true));
    }

    @Test
    void respostasTrazemCabecalhosDeSeguranca() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void trilhaDeAuditoriaEhEncadeadaESomenteInsercao() {
        transacao.executeWithoutResult(s -> {
            auditoria.registrar(EventoAuditoria.de("TESTE_A", "TESTE", 1));
            auditoria.registrar(EventoAuditoria.de("TESTE_B", "TESTE", 2).com(Map.of("chave", "valor \"aspas\"")));
        });
        assertThat(verificarCadeia.executar()).isEmpty();

        assertThatThrownBy(() -> jdbc.update("UPDATE evento_auditoria SET acao = 'ADULTERADO'"))
                .hasMessageContaining("somente-inserção");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM evento_auditoria"))
                .hasMessageContaining("somente-inserção");
    }
}
