package br.com.clinica.auditoria;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * O que aconteceu. {@code detalhes} nunca deve conter conteúdo clínico nem dados pessoais em claro.
 *
 * @param acao verbo no passado, ex.: PACIENTE_CADASTRADO, PRONTUARIO_LIDO
 * @param recursoTipo ex.: PACIENTE, PROFISSIONAL
 */
public record EventoAuditoria(String acao, String recursoTipo, Optional<String> recursoId,
                              Optional<UUID> pacienteId, Map<String, String> detalhes) {

    public EventoAuditoria {
        Objects.requireNonNull(acao, "acao");
        Objects.requireNonNull(recursoTipo, "recursoTipo");
        detalhes = detalhes == null ? Map.of() : new TreeMap<>(detalhes);
    }

    public static EventoAuditoria de(String acao, String recursoTipo, Object recursoId) {
        return new EventoAuditoria(acao, recursoTipo, Optional.of(String.valueOf(recursoId)), Optional.empty(), Map.of());
    }

    public EventoAuditoria doPaciente(UUID paciente) {
        return new EventoAuditoria(acao, recursoTipo, recursoId, Optional.of(paciente), detalhes);
    }

    public EventoAuditoria com(Map<String, String> maisDetalhes) {
        Map<String, String> todos = new TreeMap<>(detalhes);
        todos.putAll(maisDetalhes);
        return new EventoAuditoria(acao, recursoTipo, recursoId, pacienteId, todos);
    }
}
