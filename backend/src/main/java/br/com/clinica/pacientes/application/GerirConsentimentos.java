package br.com.clinica.pacientes.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.pacientes.application.PacientesPortas.ConsentimentoRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.domain.Consentimento;
import br.com.clinica.pacientes.domain.Consentimento.Finalidade;
import br.com.clinica.pacientes.domain.Paciente;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Consentimentos LGPD do próprio paciente (conceder/revogar), com histórico preservado. */
public class GerirConsentimentos {

    private final PacienteRepositorio pacientes;
    private final ConsentimentoRepositorio consentimentos;
    private final ContextoClinica contexto;
    private final ProvedorAtor provedorAtor;
    private final RegistradorAuditoria auditoria;
    private final Clock relogio;

    public GerirConsentimentos(PacienteRepositorio pacientes, ConsentimentoRepositorio consentimentos,
                               ContextoClinica contexto, ProvedorAtor provedorAtor, RegistradorAuditoria auditoria,
                               Clock relogio) {
        this.pacientes = pacientes;
        this.consentimentos = consentimentos;
        this.contexto = contexto;
        this.provedorAtor = provedorAtor;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Consentimento> meus() {
        return consentimentos.listar(contexto.atual(), meuPaciente().id());
    }

    @Transactional(readOnly = true)
    public List<Consentimento> doPaciente(UUID paciente) {
        return consentimentos.listar(contexto.atual(), paciente);
    }

    @Transactional
    public void conceder(Finalidade finalidade, String versaoTermo) {
        Paciente paciente = meuPaciente();
        consentimentos.revogar(contexto.atual(), paciente.id(), finalidade, relogio.instant());
        consentimentos.conceder(contexto.atual(), new Consentimento(UUID.randomUUID(), paciente.id(), finalidade,
                versaoTermo, relogio.instant(), Optional.empty()), "PORTAL", provedorAtor.atual().ip());
        auditoria.registrar(EventoAuditoria.de("CONSENTIMENTO_CONCEDIDO", "CONSENTIMENTO", paciente.id())
                .doPaciente(paciente.id()).com(Map.of("finalidade", finalidade.name(), "termo", versaoTermo)));
    }

    @Transactional
    public void revogar(Finalidade finalidade) {
        Paciente paciente = meuPaciente();
        if (consentimentos.revogar(contexto.atual(), paciente.id(), finalidade, relogio.instant())) {
            auditoria.registrar(EventoAuditoria.de("CONSENTIMENTO_REVOGADO", "CONSENTIMENTO", paciente.id())
                    .doPaciente(paciente.id()).com(Map.of("finalidade", finalidade.name())));
        }
    }

    private Paciente meuPaciente() {
        return pacientes.buscarPorUsuario(contexto.atual(), provedorAtor.atual().sub())
                .orElseThrow(() -> new RecursoNaoEncontradoException("paciente"));
    }
}
