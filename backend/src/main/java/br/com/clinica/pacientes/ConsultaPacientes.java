package br.com.clinica.pacientes;

import java.util.Optional;
import java.util.UUID;

/** API do módulo pacientes para outros módulos (agenda): o mínimo de dados pessoais necessário. */
public interface ConsultaPacientes {

    Optional<PacienteAgenda> porId(UUID id);

    /** Paciente vinculado à conta do portal (sub do Keycloak). */
    Optional<PacienteAgenda> porUsuario(String usuarioSub);

    /**
     * @param nome nome de exibição (nome social, se houver)
     * @param celularVerificado requisito para o paciente agendar sozinho pelo portal
     */
    record PacienteAgenda(UUID id, String nome, Optional<String> usuarioSub, boolean celularVerificado) { }
}
