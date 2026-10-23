package br.com.clinica;

import br.com.clinica.compartilhado.application.Ator;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Papel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Dublês comuns para testes unitários de casos de uso. */
public final class Fixtures {

    public static final ClinicaId CLINICA = new ClinicaId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    public static final Instant AGORA = Instant.parse("2026-10-22T12:00:00Z");
    public static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    public static final ContextoClinica CONTEXTO = () -> CLINICA;

    private Fixtures() {
    }

    public static ProvedorAtor ator(String sub, Papel... papeis) {
        Ator ator = new Ator(sub, sub, Set.of(papeis), Optional.of("10.0.0.1"), Optional.of("teste"));
        return () -> ator;
    }
}
