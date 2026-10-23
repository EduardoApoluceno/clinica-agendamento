package br.com.clinica.agenda.adapters.out.persistence;

import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.application.AgendaPortas.Transicao;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.HorarioIndisponivelException;
import br.com.clinica.agenda.domain.OrigemAgendamento;
import br.com.clinica.agenda.domain.StatusAgendamento;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Periodo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Agendamentos em SQL direto por causa do {@code tstzrange}. A colisão de horários é detectada pela
 * constraint {@code agendamento_sem_sobreposicao} (SQLSTATE 23P01), nunca só por consulta prévia.
 */
@Component
class AgendamentoRepositorioJdbc implements AgendamentoRepositorio {

    private static final String SQLSTATE_EXCLUSAO = "23P01";
    private static final String COLUNAS = """
            id, clinica_id, profissional_id, paciente_id, tipo_servico_id, lower(periodo) AS inicio,
            upper(periodo) AS fim, status, origem, hold_expira_em, preco_centavos, valor_antecipado_centavos,
            observacao, cancelado_em, motivo_cancelamento, criado_por, version""";
    private static final String OCUPAM = "('RESERVADO','AGUARDANDO_CONFIRMACAO','CONFIRMADO','PRESENTE','REALIZADO','FALTA')";

    private final JdbcTemplate jdbc;

    AgendamentoRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Agendamento> buscar(ClinicaId clinica, UUID id) {
        return jdbc.query("SELECT " + COLUNAS + " FROM agendamento WHERE clinica_id = ? AND id = ?",
                AgendamentoRepositorioJdbc::mapear, clinica.valor(), id).stream().findFirst();
    }

    @Override
    public void travarAgendaDoProfissional(ClinicaId clinica, UUID profissional) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null,
                chaveTrava(clinica, profissional));
    }

    /** Mesma chave usada pelo módulo profissionais ao registrar ausências. */
    static String chaveTrava(ClinicaId clinica, UUID profissional) {
        return "agenda:" + clinica.valor() + ":" + profissional;
    }

    @Override
    public void inserir(Agendamento a) {
        traduzindoColisao(() -> jdbc.update("""
                INSERT INTO agendamento (id, clinica_id, profissional_id, paciente_id, tipo_servico_id, periodo, status,
                    origem, hold_expira_em, preco_centavos, valor_antecipado_centavos, observacao, criado_por, version)
                VALUES (?, ?, ?, ?, ?, tstzrange(?::timestamptz, ?::timestamptz, '[)'), ?, ?, ?, ?, ?, ?, ?, 0)""",
                a.id(), a.clinicaId().valor(), a.profissionalId(), a.pacienteId(), a.servicoId(),
                Timestamp.from(a.periodo().inicio()), Timestamp.from(a.periodo().fim()), a.status().name(),
                a.origem().name(), a.holdExpiraEm().map(Timestamp::from).orElse(null), a.preco().centavos(),
                a.valorAntecipado().centavos(), a.observacao().orElse(null), a.criadoPor()));
    }

    @Override
    public Agendamento atualizar(Agendamento a) {
        int linhas = traduzindoColisao(() -> jdbc.update("""
                UPDATE agendamento SET periodo = tstzrange(?::timestamptz, ?::timestamptz, '[)'), status = ?,
                    hold_expira_em = ?, cancelado_em = ?, motivo_cancelamento = ?, atualizado_em = now(),
                    version = version + 1
                WHERE clinica_id = ? AND id = ? AND version = ?""",
                Timestamp.from(a.periodo().inicio()), Timestamp.from(a.periodo().fim()), a.status().name(),
                a.holdExpiraEm().map(Timestamp::from).orElse(null),
                a.cancelamento().map(c -> Timestamp.from(c.em())).orElse(null),
                a.cancelamento().flatMap(Agendamento.Cancelamento::motivo).orElse(null),
                a.clinicaId().valor(), a.id(), a.versao()));
        if (linhas == 0) {
            throw new ConflitoException("edicao-concorrente", "O agendamento foi alterado por outra pessoa. Recarregue.");
        }
        return new Agendamento(a.id(), a.clinicaId(), a.profissionalId(), a.pacienteId(), a.servicoId(), a.periodo(),
                a.status(), a.origem(), a.holdExpiraEm(), a.preco(), a.valorAntecipado(), a.observacao(),
                a.cancelamento(), a.criadoPor(), a.versao() + 1);
    }

    @Override
    public void registrarHistorico(ClinicaId clinica, Transicao t) {
        jdbc.update("""
                INSERT INTO historico_agendamento (id, clinica_id, agendamento_id, status_anterior, status_novo,
                    periodo_anterior, periodo_novo, ator, motivo, ocorrido_em)
                VALUES (?, ?, ?, ?, ?, tstzrange(?::timestamptz, ?::timestamptz, '[)'),
                    tstzrange(?::timestamptz, ?::timestamptz, '[)'), ?, ?, ?)""",
                UUID.randomUUID(), clinica.valor(), t.agendamentoId(), t.statusAnterior().map(Enum::name).orElse(null),
                t.statusNovo().name(), inicio(t.periodoAnterior()), fim(t.periodoAnterior()), inicio(t.periodoNovo()),
                fim(t.periodoNovo()), t.ator(), t.motivo().orElse(null), Timestamp.from(t.em()));
    }

    @Override
    public List<Agendamento> ocupando(ClinicaId clinica, UUID profissional, Periodo janela) {
        return jdbc.query("SELECT " + COLUNAS + " " + """
                 FROM agendamento WHERE clinica_id = ? AND profissional_id = ? AND status IN\s""" + OCUPAM + " " + """
                 AND periodo && tstzrange(?::timestamptz, ?::timestamptz, '[)') ORDER BY lower(periodo)""",
                AgendamentoRepositorioJdbc::mapear, clinica.valor(), profissional,
                Timestamp.from(janela.inicio()), Timestamp.from(janela.fim()));
    }

    @Override
    public List<Agendamento> iniciandoEm(ClinicaId clinica, Optional<UUID> profissional, Periodo janela) {
        return jdbc.query("SELECT " + COLUNAS + " " + """
                 FROM agendamento WHERE clinica_id = ? AND (?::uuid IS NULL OR profissional_id = ?::uuid)
                 AND lower(periodo) >= ? AND lower(periodo) < ? ORDER BY lower(periodo)""",
                AgendamentoRepositorioJdbc::mapear, clinica.valor(), profissional.orElse(null),
                profissional.orElse(null), Timestamp.from(janela.inicio()), Timestamp.from(janela.fim()));
    }

    @Override
    public List<Agendamento> doPaciente(ClinicaId clinica, UUID paciente, int limite) {
        return jdbc.query("SELECT " + COLUNAS + " " + """
                 FROM agendamento WHERE clinica_id = ? AND paciente_id = ? ORDER BY lower(periodo) DESC LIMIT ?""",
                AgendamentoRepositorioJdbc::mapear, clinica.valor(), paciente, limite);
    }

    /** SKIP LOCKED: duas instâncias do job (ou o job e um agendamento) não disputam a mesma linha. */
    @Override
    public List<Agendamento> reservasVencidas(Instant agora, int limite) {
        return jdbc.query("SELECT " + COLUNAS + " " + """
                 FROM agendamento WHERE status = 'RESERVADO' AND hold_expira_em <= ?
                 ORDER BY hold_expira_em LIMIT ? FOR UPDATE SKIP LOCKED""",
                AgendamentoRepositorioJdbc::mapear, Timestamp.from(agora), limite);
    }

    private static int traduzindoColisao(IntSupplier operacao) {
        try {
            return operacao.getAsInt();
        } catch (DataIntegrityViolationException e) {
            if (NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
                    && SQLSTATE_EXCLUSAO.equals(sql.getSQLState())) {
                throw new HorarioIndisponivelException();
            }
            throw e;
        }
    }

    private static Timestamp inicio(Optional<Periodo> periodo) {
        return periodo.map(p -> Timestamp.from(p.inicio())).orElse(null);
    }

    private static Timestamp fim(Optional<Periodo> periodo) {
        return periodo.map(p -> Timestamp.from(p.fim())).orElse(null);
    }

    private static Agendamento mapear(ResultSet rs, int linha) throws SQLException {
        Optional<String> motivo = Optional.ofNullable(rs.getString("motivo_cancelamento"));
        Optional<Agendamento.Cancelamento> cancelamento = instante(rs, "cancelado_em")
                .map(em -> new Agendamento.Cancelamento(em, motivo));
        return new Agendamento(rs.getObject("id", UUID.class), ClinicaId.de(rs.getString("clinica_id")),
                rs.getObject("profissional_id", UUID.class), rs.getObject("paciente_id", UUID.class),
                rs.getObject("tipo_servico_id", UUID.class),
                new Periodo(rs.getTimestamp("inicio").toInstant(), rs.getTimestamp("fim").toInstant()),
                StatusAgendamento.valueOf(rs.getString("status")), OrigemAgendamento.valueOf(rs.getString("origem")),
                instante(rs, "hold_expira_em"), new Dinheiro(rs.getLong("preco_centavos")),
                new Dinheiro(rs.getLong("valor_antecipado_centavos")), Optional.ofNullable(rs.getString("observacao")),
                cancelamento,
                rs.getString("criado_por"), rs.getLong("version"));
    }

    private static Optional<Instant> instante(ResultSet rs, String coluna) throws SQLException {
        return Optional.ofNullable(rs.getTimestamp(coluna)).map(Timestamp::toInstant);
    }
}
