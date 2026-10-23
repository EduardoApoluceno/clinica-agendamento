package br.com.clinica.auditoria.application;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificarCadeiaAuditoriaTest {

    private final CadeiaAuditoria cadeia = mock(CadeiaAuditoria.class);
    private final VerificarCadeiaAuditoria caso = new VerificarCadeiaAuditoria(cadeia);

    @Test
    void cadeiaIntegra() {
        when(cadeia.primeiroRegistroCorrompido()).thenReturn(Optional.empty());
        when(cadeia.totalRegistros()).thenReturn(10L);
        assertThat(caso.executar()).isEmpty();
    }

    @Test
    void apontaPrimeiroRegistroAdulterado() {
        when(cadeia.primeiroRegistroCorrompido()).thenReturn(Optional.of(5L));
        assertThat(caso.executar()).contains(5L);
    }
}
