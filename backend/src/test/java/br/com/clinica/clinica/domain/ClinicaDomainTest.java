package br.com.clinica.clinica.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicaDomainTest {

    static Endereco endereco() {
        return new Endereco("Av. Sete de Setembro", "1000", Optional.of(" "), "Centro", "Porto Velho", "RO", "76801-000");
    }

    @Test
    void cnpjValidoComPontuacao() {
        assertThat(Cnpj.de("11.222.333/0001-81").digitos()).isEqualTo("11222333000181");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11222333000182", "00000000000000", "123"})
    void cnpjInvalido(String valor) {
        assertThatThrownBy(() -> Cnpj.de(valor)).isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void enderecoNormalizaCepEComplementoVazio() {
        Endereco e = endereco();
        assertThat(e.cep()).isEqualTo("76801000");
        assertThat(e.complemento()).isEmpty();
    }

    @Test
    void enderecoExigeCamposEUfECep() {
        assertThatThrownBy(() -> new Endereco("", "1", Optional.empty(), "b", "c", "RO", "76801000"))
                .isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new Endereco("a", "1", Optional.empty(), "b", "c", "ro", "76801000"))
                .isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new Endereco("a", "1", Optional.empty(), "b", "c", "RO", "123"))
                .isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void clinicaValidaNomeEAtualizaDados() {
        var clinica = new Clinica(new ClinicaId(UUID.randomUUID()), "  Vida  ", Cnpj.de("11222333000181"),
                ZoneId.of("America/Porto_Velho"), endereco(), Optional.of(""), 3);
        assertThat(clinica.nome()).isEqualTo("Vida");
        assertThat(clinica.telefone()).isEmpty();
        var alterada = clinica.comDados("Nova", clinica.cnpj(), endereco(), Optional.of("(69) 3333-0000"));
        assertThat(alterada.nome()).isEqualTo("Nova");
        assertThat(alterada.versao()).isEqualTo(3);
        assertThatThrownBy(() -> clinica.comDados(" ", clinica.cnpj(), endereco(), Optional.empty()))
                .isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void configuracaoPadraoEValidacoes() {
        ConfiguracaoAgenda padrao = ConfiguracaoAgenda.padrao();
        assertThat(padrao.minutosHoldPagamento()).isEqualTo(15);
        assertThat(padrao.lembretes()).containsExactly(Duration.ofHours(24), Duration.ofHours(2));
        assertThatThrownBy(() -> comHold(4)).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> comHold(61)).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new ConfiguracaoAgenda(Duration.ofHours(-1), Duration.ZERO, Duration.ofDays(1), 15,
                List.of(), Duration.ofMinutes(5), false, true)).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new ConfiguracaoAgenda(Duration.ZERO, Duration.ZERO, Duration.ofDays(400), 15,
                List.of(), Duration.ofMinutes(5), false, true)).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new ConfiguracaoAgenda(Duration.ZERO, Duration.ZERO, Duration.ofDays(1), 15,
                List.of(Duration.ZERO), Duration.ofMinutes(5), false, true)).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new ConfiguracaoAgenda(Duration.ZERO, Duration.ZERO, Duration.ofDays(1), 15,
                List.of(), Duration.ZERO, false, true)).isInstanceOf(DadoInvalidoException.class);
    }

    private static ConfiguracaoAgenda comHold(int minutos) {
        return new ConfiguracaoAgenda(Duration.ZERO, Duration.ZERO, Duration.ofDays(1), minutos, List.of(),
                Duration.ofMinutes(5), false, true);
    }
}
