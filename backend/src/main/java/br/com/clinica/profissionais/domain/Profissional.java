package br.com.clinica.profissionais.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Profissional de saúde da clínica.
 *
 * @param usuarioSub conta (Keycloak) vinculada, usada para "minha agenda" e permissões do próprio
 * @param especialidades ids das especialidades
 * @param servicos ids dos tipos de serviço que atende
 */
public record Profissional(UUID id, ClinicaId clinicaId, Optional<String> usuarioSub, String nome,
                           RegistroConselho registro, Set<UUID> especialidades, Set<UUID> servicos,
                           boolean ativo, long versao) {

    private static final int TAMANHO_MAXIMO_NOME = 160;

    public Profissional {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clinicaId, "clinicaId");
        Objects.requireNonNull(registro, "registro");
        if (nome == null || nome.isBlank() || nome.length() > TAMANHO_MAXIMO_NOME) {
            throw new DadoInvalidoException("nome-invalido", "Informe o nome (até 160 caracteres).");
        }
        nome = nome.strip();
        usuarioSub = usuarioSub == null ? Optional.empty() : usuarioSub.filter(s -> !s.isBlank());
        especialidades = Set.copyOf(especialidades);
        servicos = Set.copyOf(servicos);
    }

    public static Profissional novo(ClinicaId clinica, DadosProfissional dados) {
        return new Profissional(UUID.randomUUID(), clinica, dados.usuarioSub(), dados.nome(), dados.registro(),
                dados.especialidades(), dados.servicos(), true, 0);
    }

    public Profissional atualizado(DadosProfissional dados) {
        return new Profissional(id, clinicaId, dados.usuarioSub(), dados.nome(), dados.registro(),
                dados.especialidades(), dados.servicos(), ativo, versao);
    }

    public Profissional comAtivo(boolean novoAtivo) {
        return new Profissional(id, clinicaId, usuarioSub, nome, registro, especialidades, servicos, novoAtivo, versao);
    }

    public boolean pertenceA(String sub) {
        return usuarioSub.filter(sub::equals).isPresent();
    }

    /** Dados editáveis do profissional. */
    public record DadosProfissional(String nome, RegistroConselho registro, Optional<String> usuarioSub,
                                    Set<UUID> especialidades, Set<UUID> servicos) { }
}
