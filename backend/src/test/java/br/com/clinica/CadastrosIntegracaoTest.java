package br.com.clinica;

import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.Permissao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxos da Fase 1 com PostgreSQL real: RBAC, IDOR, criptografia em repouso e auditoria. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfraestruturaContainers.class)
class CadastrosIntegracaoTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper json;

    /** Sessão OIDC simulada com as autoridades reais que o mapeador geraria para o papel. */
    static RequestPostProcessor como(String sub, Papel papel) {
        List<GrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority(papel.autoridade()));
        papel.permissoes().stream().map(Permissao::autoridade).map(SimpleGrantedAuthority::new).forEach(autoridades::add);
        return oidcLogin().idToken(t -> t.subject(sub).claims(c -> c.putAll(Map.of("name", sub))))
                .authorities(autoridades);
    }

    private JsonNode postar(String url, RequestPostProcessor usuario, String corpo, int statusEsperado) throws Exception {
        String resposta = mvc.perform(post(url).with(usuario).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(statusEsperado))
                .andReturn().getResponse().getContentAsString();
        return resposta.isBlank() ? null : json.readTree(resposta);
    }

    @Test
    void recepcaoCadastraPacienteComCpfCifradoEProfissionalVeMascarado() throws Exception {
        JsonNode paciente = postar("/api/v1/pacientes", como("recepcao", Papel.RECEPCAO), """
                {"dados":{"nome":"Maria Teste","dataNascimento":"1985-02-03","cpf":"111.444.777-35",
                 "canalPreferido":"WHATSAPP"},"contatos":[{"tipo":"CELULAR","valor":"(69) 99111-2222","principal":true}]}
                """, 201);
        String id = paciente.get("id").asString();

        byte[] cpfNoBanco = jdbc.queryForObject("SELECT cpf_cifrado FROM paciente WHERE id = ?::uuid", byte[].class, id);
        assertThat(new String(cpfNoBanco, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("11144477735");

        mvc.perform(get("/api/v1/pacientes/" + id).with(como("dra.teste", Papel.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value("***.444.777-**"));
        mvc.perform(get("/api/v1/pacientes?termo=11144477735").with(como("recepcao", Papel.RECEPCAO)))
                .andExpect(jsonPath("$.total").value(1));

        Integer leituras = jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE acao = 'PACIENTE_CONSULTADO' "
                + "AND paciente_id = ?::uuid AND ator_sub = 'dra.teste'", Integer.class, id);
        assertThat(leituras).isEqualTo(1);
    }

    @Test
    void matrizDePermissoes() throws Exception {
        mvc.perform(get("/api/v1/pacientes?termo=abc").with(como("p1", Papel.PACIENTE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auditoria").with(como("r1", Papel.RECEPCAO))).andExpect(status().isForbidden());
        postar("/api/v1/profissionais", como("r1", Papel.RECEPCAO),
                "{\"nome\":\"X\",\"conselho\":\"CRM\",\"numeroConselho\":\"1\",\"ufConselho\":\"RO\"}", 403);
        mvc.perform(get("/api/v1/me/paciente").with(como("r1", Papel.RECEPCAO))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auditoria").with(como("a1", Papel.ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/publico/clinica")).andExpect(status().isOk());
    }

    @Test
    void pacienteSoEnxergaOProprioCadastroEVerificaCelular() throws Exception {
        RequestPostProcessor ana = como("conta-ana", Papel.PACIENTE);
        mvc.perform(get("/api/v1/me/paciente").with(ana)).andExpect(status().isNotFound());
        postar("/api/v1/me/paciente", ana, """
                {"dados":{"nome":"Ana Paciente","dataNascimento":"1990-07-01","cpf":"935.411.347-80",
                 "canalPreferido":"WHATSAPP"},"celular":"(69) 99333-4444"}""", 201);
        JsonNode enviado = postar("/api/v1/me/telefone/codigo", ana, "{\"celular\":\"(69) 99333-4444\"}", 200);
        assertThat(enviado.get("codigoDemo").isNull()).isTrue();

        mvc.perform(get("/api/v1/me/paciente").with(como("conta-outra", Papel.PACIENTE))).andExpect(status().isNotFound());
        postar("/api/v1/me/telefone/verificacao", ana, "{\"codigo\":\"000000\"}", 422);
    }

    @Test
    void validacaoRetornaProblemDetails() throws Exception {
        postar("/api/v1/pacientes", como("recepcao", Papel.RECEPCAO), """
                {"dados":{"nome":"Cpf Ruim","dataNascimento":"1985-02-03","cpf":"123.456.789-00","canalPreferido":"SMS"}}
                """, 422);
        postar("/api/v1/especialidades", como("admin", Papel.ADMIN), "{\"nome\":\"\"}", 400);
    }
}
