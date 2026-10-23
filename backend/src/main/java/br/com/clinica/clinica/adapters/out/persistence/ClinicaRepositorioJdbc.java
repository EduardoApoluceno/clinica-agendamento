package br.com.clinica.clinica.adapters.out.persistence;

import br.com.clinica.clinica.application.ClinicaRepositorio;
import br.com.clinica.clinica.domain.Clinica;
import br.com.clinica.clinica.domain.Cnpj;
import br.com.clinica.clinica.domain.ConfiguracaoAgenda;
import br.com.clinica.clinica.domain.Endereco;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
class ClinicaRepositorioJdbc implements ClinicaRepositorio {

    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() { };
    private static final String EM_SEGUNDOS = "extract(epoch FROM %s)::bigint AS %s";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    ClinicaRepositorioJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Clinica> buscar(ClinicaId id) {
        return jdbc.query("SELECT id, nome, cnpj, fuso_horario, endereco::text AS endereco, telefone, version "
                        + "FROM clinica WHERE id = ?", (rs, n) -> mapearClinica(rs), id.valor())
                .stream().findFirst();
    }

    @Override
    public void criar(Clinica c, ConfiguracaoAgenda cfg) {
        jdbc.update("INSERT INTO clinica (id, nome, cnpj, fuso_horario, endereco, telefone) VALUES (?, ?, ?, ?, ?::jsonb, ?)",
                c.id().valor(), c.nome(), c.cnpj().digitos(), c.fusoHorario().getId(), enderecoJson(c.endereco()),
                c.telefone().orElse(null));
        jdbc.update("INSERT INTO configuracao_clinica (clinica_id) VALUES (?)", c.id().valor());
        atualizarConfiguracao(c.id(), cfg, 0);
    }

    @Override
    public Clinica atualizar(Clinica c) {
        int linhas = jdbc.update("""
                UPDATE clinica SET nome = ?, cnpj = ?, endereco = ?::jsonb, telefone = ?, version = version + 1
                WHERE id = ? AND version = ?""",
                c.nome(), c.cnpj().digitos(), enderecoJson(c.endereco()), c.telefone().orElse(null),
                c.id().valor(), c.versao());
        exigirAtualizacao(linhas);
        return buscar(c.id()).orElseThrow(() -> new RecursoNaoEncontradoException("clinica"));
    }

    @Override
    public ConfiguracaoComVersao configuracao(ClinicaId id) {
        String sql = "SELECT " + String.join(", ",
                EM_SEGUNDOS.formatted("antecedencia_min_cancelamento", "cancelamento"),
                EM_SEGUNDOS.formatted("antecedencia_min_agendamento", "agendamento"),
                EM_SEGUNDOS.formatted("horizonte_agendamento", "horizonte"),
                EM_SEGUNDOS.formatted("prazo_aceite_lista_espera", "lista_espera"))
                + ", minutos_hold_pagamento, lembretes_minutos, sms_fallback_ativo,"
                + " exige_justificativa_prontuario_sem_vinculo, version FROM configuracao_clinica WHERE clinica_id = ?";
        return jdbc.query(sql, (rs, n) -> mapearConfiguracao(rs), id.valor()).stream().findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("configuracao"));
    }

    @Override
    public ConfiguracaoComVersao atualizarConfiguracao(ClinicaId id, ConfiguracaoAgenda c, long versao) {
        Integer[] lembretes = c.lembretes().stream().map(d -> (int) d.toMinutes()).toArray(Integer[]::new);
        int linhas = jdbc.update("""
                UPDATE configuracao_clinica SET
                    antecedencia_min_cancelamento = make_interval(secs => ?),
                    antecedencia_min_agendamento = make_interval(secs => ?),
                    horizonte_agendamento = make_interval(secs => ?),
                    prazo_aceite_lista_espera = make_interval(secs => ?),
                    minutos_hold_pagamento = ?, lembretes_minutos = ?, sms_fallback_ativo = ?,
                    exige_justificativa_prontuario_sem_vinculo = ?, version = version + 1
                WHERE clinica_id = ? AND version = ?""",
                c.antecedenciaMinimaCancelamento().toSeconds(), c.antecedenciaMinimaAgendamento().toSeconds(),
                c.horizonteAgendamento().toSeconds(), c.prazoAceiteListaEspera().toSeconds(),
                c.minutosHoldPagamento(), lembretes, c.smsFallbackAtivo(),
                c.exigeJustificativaProntuarioSemVinculo(), id.valor(), versao);
        exigirAtualizacao(linhas);
        return configuracao(id);
    }

    private static void exigirAtualizacao(int linhas) {
        if (linhas == 0) {
            throw new ConflitoException("edicao-concorrente", "Os dados foram alterados por outra pessoa. Recarregue.");
        }
    }

    private Clinica mapearClinica(ResultSet rs) throws SQLException {
        Map<String, String> e = json.readValue(rs.getString("endereco"), MAPA);
        Endereco endereco = new Endereco(e.get("logradouro"), e.get("numero"), Optional.ofNullable(e.get("complemento")),
                e.get("bairro"), e.get("cidade"), e.get("uf"), e.get("cep"));
        return new Clinica(new ClinicaId(rs.getObject("id", java.util.UUID.class)), rs.getString("nome"),
                new Cnpj(rs.getString("cnpj")), ZoneId.of(rs.getString("fuso_horario")), endereco,
                Optional.ofNullable(rs.getString("telefone")), rs.getLong("version"));
    }

    private static ConfiguracaoComVersao mapearConfiguracao(ResultSet rs) throws SQLException {
        Array lembretes = rs.getArray("lembretes_minutos");
        List<Duration> duracoes = Arrays.stream((Integer[]) lembretes.getArray())
                .map(m -> Duration.ofMinutes(m)).toList();
        var configuracao = new ConfiguracaoAgenda(
                Duration.ofSeconds(rs.getLong("cancelamento")), Duration.ofSeconds(rs.getLong("agendamento")),
                Duration.ofSeconds(rs.getLong("horizonte")), rs.getInt("minutos_hold_pagamento"), duracoes,
                Duration.ofSeconds(rs.getLong("lista_espera")), rs.getBoolean("sms_fallback_ativo"),
                rs.getBoolean("exige_justificativa_prontuario_sem_vinculo"));
        return new ConfiguracaoComVersao(configuracao, rs.getLong("version"));
    }

    private String enderecoJson(Endereco e) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("logradouro", e.logradouro());
        campos.put("numero", e.numero());
        e.complemento().ifPresent(c -> campos.put("complemento", c));
        campos.put("bairro", e.bairro());
        campos.put("cidade", e.cidade());
        campos.put("uf", e.uf());
        campos.put("cep", e.cep());
        return json.writeValueAsString(campos);
    }
}
