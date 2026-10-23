package br.com.clinica;

import br.com.clinica.compartilhado.domain.Papel;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static br.com.clinica.CadastrosIntegracaoTest.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Agenda com PostgreSQL real: constraint anti-overbooking sob concorrência (ADR-0004), RBAC e IDOR. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfraestruturaContainers.class)
class AgendaIntegracaoTest {

    private static final ZoneId FUSO = ZoneId.of("America/Porto_Velho");
    private static final UUID CLINICA = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper json;

    private final RequestPostProcessor recepcao = como("recepcao", Papel.RECEPCAO);

    /** Profissional que atende todos os dias das 07:00 às 19:00 um serviço de 30 min sem pagamento antecipado. */
    record Cenario(UUID servico, UUID profissional, String subProfissional) { }

    private Cenario cenario() {
        UUID servico = UUID.randomUUID();
        UUID profissional = UUID.randomUUID();
        String sub = "prof-" + profissional;
        jdbc.update("""
                INSERT INTO tipo_servico (id, clinica_id, nome, duracao_minutos, preco_centavos, politica_pagamento)
                VALUES (?, ?, ?, 30, 20000, 'NENHUM')""", servico, CLINICA, "Consulta " + servico);
        jdbc.update("""
                INSERT INTO profissional (id, clinica_id, usuario_sub, nome, conselho, numero_conselho, uf_conselho)
                VALUES (?, ?, ?, 'Dra. Teste', 'CRM', ?, 'RO')""", profissional, CLINICA, sub,
                String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000)));
        jdbc.update("INSERT INTO profissional_servico VALUES (?, ?)", profissional, servico);
        for (int dia = 1; dia <= 7; dia++) {
            jdbc.update("""
                    INSERT INTO expediente_faixa (id, clinica_id, profissional_id, dia_semana, hora_inicio, hora_fim)
                    VALUES (?, ?, ?, ?, '07:00', '19:00')""", UUID.randomUUID(), CLINICA, profissional, dia);
        }
        return new Cenario(servico, profissional, sub);
    }

    private String paciente() throws Exception {
        JsonNode p = postar("/api/v1/pacientes", recepcao, """
                {"dados":{"nome":"Paciente Agenda","dataNascimento":"1980-01-01","cpf":"%s","canalPreferido":"WHATSAPP"}}
                """.formatted(cpfAleatorio()), 201);
        return p.get("id").asString();
    }

    private static String daquiA(int dias, int hora) {
        return LocalDate.now(FUSO).plusDays(dias).atTime(LocalTime.of(hora, 0)).atZone(FUSO).toInstant().toString();
    }

    private JsonNode postar(String url, RequestPostProcessor usuario, String corpo, int statusEsperado) throws Exception {
        String resposta = mvc.perform(post(url).with(usuario).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(statusEsperado))
                .andReturn().getResponse().getContentAsString();
        return resposta.isBlank() ? null : json.readTree(resposta);
    }

    private static String novo(Cenario c, String inicio, String paciente) {
        return """
                {"servicoId":"%s","profissionalId":"%s","inicio":"%s","pacienteId":"%s"}"""
                .formatted(c.servico(), c.profissional(), inicio, paciente);
    }

    @Test
    void vinteRequisicoesSimultaneasNoMesmoHorarioGeramUmAgendamento() throws Exception {
        Cenario c = cenario();
        String paciente = paciente();
        String corpo = novo(c, daquiA(3, 10), paciente);
        int tentativas = 20;
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> respostas = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(tentativas)) {
            for (int i = 0; i < tentativas; i++) {
                respostas.add(executor.submit(() -> {
                    largada.await();
                    return mvc.perform(post("/api/v1/agendamentos").with(recepcao).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
            List<Integer> codigos = new ArrayList<>();
            for (Future<Integer> r : respostas) {
                codigos.add(r.get());
            }
            assertThat(codigos).containsOnly(201, 409);
            assertThat(codigos).filteredOn(s -> s == 201).hasSize(1);
        }
        Integer gravados = jdbc.queryForObject("SELECT count(*) FROM agendamento WHERE profissional_id = ?",
                Integer.class, c.profissional());
        assertThat(gravados).isEqualTo(1);
    }

    @Test
    void recepcaoAgendaRemarcaECancelaComHistoricoEAcessoRestrito() throws Exception {
        Cenario c = cenario();
        String paciente = paciente();
        JsonNode criado = postar("/api/v1/agendamentos", recepcao, novo(c, daquiA(2, 9), paciente), 201);
        String id = criado.get("id").asString();
        assertThat(criado.get("status").asString()).isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(criado.get("paciente").asString()).isEqualTo("Paciente Agenda");

        postar("/api/v1/agendamentos", recepcao, novo(c, daquiA(2, 9), paciente), 409);
        mvc.perform(get("/api/v1/publico/disponibilidade").param("servicoId", c.servico().toString())
                        .param("profissionalId", c.profissional().toString())
                        .param("de", LocalDate.now(FUSO).plusDays(2).toString())
                        .param("ate", LocalDate.now(FUSO).plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.inicio == '%s')].livre".formatted(daquiA(2, 9))).value(false));

        String data = LocalDate.now(FUSO).plusDays(2).toString();
        mvc.perform(get("/api/v1/agenda/dia").param("data", data).with(como(c.subProfissional(), Papel.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colunas.length()").value(1))
                .andExpect(jsonPath("$.colunas[0].agendamentos[0].id").value(id));
        mvc.perform(get("/api/v1/agenda/dia").param("data", data).with(como("p1", Papel.PACIENTE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/agendamentos/" + id).with(como("outro-paciente", Papel.PACIENTE)))
                .andExpect(status().isNotFound());

        postar("/api/v1/agendamentos/" + id + "/remarcacao", recepcao,
                "{\"inicio\":\"%s\",\"versao\":7}".formatted(daquiA(2, 11)), 409);
        JsonNode remarcado = postar("/api/v1/agendamentos/" + id + "/remarcacao", recepcao,
                "{\"inicio\":\"%s\",\"versao\":0}".formatted(daquiA(2, 11)), 200);
        assertThat(remarcado.get("versao").asLong()).isEqualTo(1);
        postar("/api/v1/agendamentos/" + id + "/remarcacao", recepcao,
                "{\"inicio\":\"%s\",\"versao\":1}".formatted(daquiA(2, 20)), 422);

        JsonNode cancelado = postar("/api/v1/agendamentos/" + id + "/cancelamento", recepcao,
                "{\"motivo\":\"Paciente pediu\"}", 200);
        assertThat(cancelado.get("status").asString()).isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historico_agendamento WHERE agendamento_id = ?::uuid",
                Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE recurso_id = ?",
                Integer.class, id)).isEqualTo(3);

        // Horário cancelado volta a ficar livre.
        postar("/api/v1/agendamentos", recepcao, novo(c, daquiA(2, 11), paciente), 201);
    }

    @Test
    void pacienteComCelularVerificadoAgendaParaSiEBaixaOCalendario() throws Exception {
        Cenario c = cenario();
        RequestPostProcessor conta = como("conta-" + UUID.randomUUID(), Papel.PACIENTE);
        String corpo = """
                {"servicoId":"%s","profissionalId":"%s","inicio":"%s"}""".formatted(c.servico(), c.profissional(),
                daquiA(4, 15));
        postar("/api/v1/agendamentos", conta, corpo, 422);

        JsonNode cadastro = postar("/api/v1/me/paciente", conta, """
                {"dados":{"nome":"Paula Portal","dataNascimento":"1990-07-01","cpf":"%s","canalPreferido":"WHATSAPP"},
                 "celular":"(69) 9%s"}""".formatted(cpfAleatorio(),
                ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999)), 201);
        postar("/api/v1/agendamentos", conta, corpo, 422);
        jdbc.update("UPDATE contato_paciente SET verificado_em = now() WHERE paciente_id = ?::uuid",
                cadastro.get("id").asString());

        JsonNode criado = postar("/api/v1/agendamentos", conta, corpo, 201);
        assertThat(criado.get("origem").asString()).isEqualTo("PORTAL");
        mvc.perform(get("/api/v1/me/agendamentos").with(conta))
                .andExpect(jsonPath("$[0].id").value(criado.get("id").asString()));
        mvc.perform(get("/api/v1/agendamentos/" + criado.get("id").asString() + "/calendario.ics").with(conta))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN:VEVENT")));
        postar("/api/v1/agendamentos/" + criado.get("id").asString() + "/presenca", conta, "", 403);
    }

    /** CPF com dígitos verificadores válidos. */
    static String cpfAleatorio() {
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) {
            d[i] = ThreadLocalRandom.current().nextInt(10);
        }
        d[9] = digito(d, 9);
        d[10] = digito(d, 10);
        StringBuilder cpf = new StringBuilder();
        for (int n : d) {
            cpf.append(n);
        }
        return cpf.toString();
    }

    private static int digito(int[] d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += d[i] * (tamanho + 1 - i);
        }
        return soma * 10 % 11 % 10;
    }
}
