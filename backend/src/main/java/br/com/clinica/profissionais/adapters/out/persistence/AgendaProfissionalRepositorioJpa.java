package br.com.clinica.profissionais.adapters.out.persistence;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.profissionais.application.ProfissionaisPortas.AgendaProfissionalRepositorio;
import br.com.clinica.profissionais.domain.Ausencia;
import br.com.clinica.profissionais.domain.Expediente;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class AgendaProfissionalRepositorioJpa implements AgendaProfissionalRepositorio {

    private final ExpedienteFaixaJpa faixas;
    private final AusenciaJpa ausencias;
    private final JdbcTemplate jdbc;

    AgendaProfissionalRepositorioJpa(ExpedienteFaixaJpa faixas, AusenciaJpa ausencias, JdbcTemplate jdbc) {
        this.faixas = faixas;
        this.ausencias = ausencias;
        this.jdbc = jdbc;
    }

    @Override
    public Expediente expediente(ClinicaId clinica, UUID profissional) {
        return new Expediente(faixas
                .findByClinicaIdAndProfissionalIdOrderByDiaSemanaAscHoraInicioAsc(clinica.valor(), profissional).stream()
                .map(f -> new Expediente.Faixa(DayOfWeek.of(f.diaSemana), f.horaInicio, f.horaFim)).toList());
    }

    @Override
    public void substituirExpediente(ClinicaId clinica, UUID profissional, Expediente expediente) {
        faixas.removerDoProfissional(clinica.valor(), profissional);
        faixas.saveAll(expediente.faixas().stream().map(f -> {
            ExpedienteFaixaEntity e = new ExpedienteFaixaEntity();
            e.id = UUID.randomUUID();
            e.clinicaId = clinica.valor();
            e.profissionalId = profissional;
            e.diaSemana = (short) f.dia().getValue();
            e.horaInicio = f.inicio();
            e.horaFim = f.fim();
            return e;
        }).toList());
    }

    @Override
    public List<Ausencia> ausencias(ClinicaId clinica, UUID profissional, Periodo janela) {
        return ausencias.naJanela(clinica.valor(), profissional, janela.inicio(), janela.fim()).stream()
                .map(a -> new Ausencia(a.id, a.profissionalId, Ausencia.Tipo.valueOf(a.tipo),
                        new Periodo(a.inicio, a.fim), Optional.ofNullable(a.motivo)))
                .toList();
    }

    @Override
    public void salvarAusencia(ClinicaId clinica, Ausencia ausencia, String criadoPor) {
        // Mesma trava que o módulo agenda pega ao marcar horário (ADR-0004): ausência × agendamento em série.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null,
                "agenda:" + clinica.valor() + ":" + ausencia.profissionalId());
        AusenciaEntity e = new AusenciaEntity();
        e.id = ausencia.id();
        e.clinicaId = clinica.valor();
        e.profissionalId = ausencia.profissionalId();
        e.tipo = ausencia.tipo().name();
        e.inicio = ausencia.periodo().inicio();
        e.fim = ausencia.periodo().fim();
        e.motivo = ausencia.motivo().orElse(null);
        e.criadoPor = criadoPor;
        ausencias.save(e);
    }

    @Override
    public boolean removerAusencia(ClinicaId clinica, UUID profissional, UUID ausencia) {
        return ausencias.remover(clinica.valor(), profissional, ausencia) > 0;
    }
}
