package br.com.clinica.compartilhado.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    @Test
    void reaproveitaIdValidoRecebido() {
        assertThat(CorrelationIdFilter.resolver("abc-12345-def")).isEqualTo("abc-12345-def");
    }

    @Test
    void geraNovoIdQuandoAusente() {
        assertThat(CorrelationIdFilter.resolver(null)).hasSize(36);
    }

    @Test
    void descartaValorComCaracteresPerigosos() {
        String malicioso = "abc\n2026-01-01 INFO usuario=admin";
        assertThat(CorrelationIdFilter.resolver(malicioso)).isNotEqualTo(malicioso).hasSize(36);
    }
}
