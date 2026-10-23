package br.com.clinica.profissionais.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.Ator;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.compartilhado.domain.Permissao;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.profissionais.application.ProfissionaisPortas.AgendaProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.ProfissionalRepositorio;
import br.com.clinica.profissionais.domain.Ausencia;
import br.com.clinica.profissionais.domain.Expediente;
import br.com.clinica.profissionais.domain.Profissional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expediente e ausências. Quem pode alterar: quem tem a permissão ampla correspondente, ou o
 * próprio profissional (permissão "agenda-propria:gerir" + conta vinculada ao cadastro).
 */
public class GerirAgendaProfissional {

    private final ProfissionalRepositorio profissionais;
    private final AgendaProfissionalRepositorio agenda;
    private final ContextoClinica contexto;
    private final ProvedorAtor provedorAtor;
    private final RegistradorAuditoria auditoria;

    public GerirAgendaProfissional(ProfissionalRepositorio profissionais, AgendaProfissionalRepositorio agenda,
                                   ContextoClinica contexto, ProvedorAtor provedorAtor, RegistradorAuditoria auditoria) {
        this.profissionais = profissionais;
        this.agenda = agenda;
        this.contexto = contexto;
        this.provedorAtor = provedorAtor;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Expediente expediente(UUID profissional) {
        exigirProfissional(profissional);
        return agenda.expediente(contexto.atual(), profissional);
    }

    @Transactional
    public Expediente definirExpediente(UUID profissional, Expediente expediente) {
        exigirAlteracao(exigirProfissional(profissional), Permissao.PROFISSIONAIS_ADMINISTRAR);
        agenda.substituirExpediente(contexto.atual(), profissional, expediente);
        auditoria.registrar(EventoAuditoria.de("EXPEDIENTE_DEFINIDO", "PROFISSIONAL", profissional)
                .com(Map.of("faixas", String.valueOf(expediente.faixas().size()))));
        return expediente;
    }

    @Transactional(readOnly = true)
    public List<Ausencia> ausencias(UUID profissional, Periodo janela) {
        exigirProfissional(profissional);
        return agenda.ausencias(contexto.atual(), profissional, janela);
    }

    @Transactional
    public Ausencia registrarAusencia(UUID profissional, Ausencia.Tipo tipo, Periodo periodo, Optional<String> motivo) {
        exigirAlteracao(exigirProfissional(profissional), Permissao.AUSENCIAS_GERIR);
        Ausencia ausencia = Ausencia.nova(profissional, tipo, periodo, motivo);
        agenda.salvarAusencia(contexto.atual(), ausencia, provedorAtor.atual().sub());
        auditoria.registrar(EventoAuditoria.de("AUSENCIA_REGISTRADA", "AUSENCIA", ausencia.id())
                .com(Map.of("profissional", profissional.toString(), "tipo", tipo.name())));
        return ausencia;
    }

    @Transactional
    public void removerAusencia(UUID profissional, UUID ausencia) {
        exigirAlteracao(exigirProfissional(profissional), Permissao.AUSENCIAS_GERIR);
        if (!agenda.removerAusencia(contexto.atual(), profissional, ausencia)) {
            throw new RecursoNaoEncontradoException("ausencia");
        }
        auditoria.registrar(EventoAuditoria.de("AUSENCIA_REMOVIDA", "AUSENCIA", ausencia)
                .com(Map.of("profissional", profissional.toString())));
    }

    private Profissional exigirProfissional(UUID id) {
        return profissionais.buscar(contexto.atual(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional"));
    }

    /** Responde "não encontrado" a quem não pode alterar, para não revelar dados de terceiros. */
    private void exigirAlteracao(Profissional profissional, Permissao permissaoAmpla) {
        Ator ator = provedorAtor.atual();
        boolean amplo = ator.papeis().stream().anyMatch(p -> p.permissoes().contains(permissaoAmpla));
        boolean proprio = ator.possui(Papel.PROFISSIONAL) && profissional.pertenceA(ator.sub());
        if (!amplo && !proprio) {
            throw new RecursoNaoEncontradoException("profissional");
        }
    }
}
