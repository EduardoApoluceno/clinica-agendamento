package br.com.clinica.profissionais.application;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.profissionais.domain.Ausencia;
import br.com.clinica.profissionais.domain.Expediente;
import br.com.clinica.profissionais.domain.Profissional;
import br.com.clinica.profissionais.domain.TipoServico;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Portas de saída do módulo profissionais. Toda consulta é restrita à clínica informada. */
public final class ProfissionaisPortas {

    private ProfissionaisPortas() {
    }

    public interface ProfissionalRepositorio {
        Optional<Profissional> buscar(ClinicaId clinica, UUID id);

        Optional<Profissional> buscarPorUsuario(ClinicaId clinica, String usuarioSub);

        List<Profissional> listar(ClinicaId clinica, boolean apenasAtivos);

        /** Cria ou atualiza; lança ConflitoException se a versão divergir ou o registro já existir. */
        Profissional salvar(Profissional profissional);
    }

    public interface TipoServicoRepositorio {
        Optional<TipoServico> buscar(ClinicaId clinica, UUID id);

        List<TipoServico> listar(ClinicaId clinica, boolean apenasAtivos);

        TipoServico salvar(TipoServico servico);

        boolean existemTodos(ClinicaId clinica, Set<UUID> ids);
    }

    public interface EspecialidadeRepositorio {
        List<Especialidade> listar(ClinicaId clinica);

        Especialidade criar(ClinicaId clinica, String nome);

        boolean existemTodas(ClinicaId clinica, Set<UUID> ids);
    }

    public interface AgendaProfissionalRepositorio {
        Expediente expediente(ClinicaId clinica, UUID profissional);

        void substituirExpediente(ClinicaId clinica, UUID profissional, Expediente expediente);

        List<Ausencia> ausencias(ClinicaId clinica, UUID profissional, Periodo janela);

        void salvarAusencia(ClinicaId clinica, Ausencia ausencia, String criadoPor);

        boolean removerAusencia(ClinicaId clinica, UUID profissional, UUID ausencia);
    }

    /** Especialidade médica/clínica. */
    public record Especialidade(UUID id, String nome) { }
}
