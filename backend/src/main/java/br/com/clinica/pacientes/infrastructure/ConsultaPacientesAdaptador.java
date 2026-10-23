package br.com.clinica.pacientes.infrastructure;

import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.pacientes.ConsultaPacientes;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.domain.Paciente;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expõe só nome e vínculo; CPF e contatos não saem do módulo. */
@Component
@Transactional(readOnly = true)
class ConsultaPacientesAdaptador implements ConsultaPacientes {

    private final PacienteRepositorio pacientes;
    private final ContextoClinica contexto;

    ConsultaPacientesAdaptador(PacienteRepositorio pacientes, ContextoClinica contexto) {
        this.pacientes = pacientes;
        this.contexto = contexto;
    }

    @Override
    public Optional<PacienteAgenda> porId(UUID id) {
        return pacientes.buscar(contexto.atual(), id).map(ConsultaPacientesAdaptador::paraAgenda);
    }

    @Override
    public Optional<PacienteAgenda> porUsuario(String usuarioSub) {
        return pacientes.buscarPorUsuario(contexto.atual(), usuarioSub).map(ConsultaPacientesAdaptador::paraAgenda);
    }

    private static PacienteAgenda paraAgenda(Paciente p) {
        return new PacienteAgenda(p.id(), p.dados().nomeExibicao(), p.usuarioSub(), p.celularVerificado().isPresent());
    }
}
