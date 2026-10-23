package br.com.clinica.compartilhado.domain;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Nested
    class CpfTest {
        @Test
        void aceitaComOuSemPontuacaoEFormata() {
            Cpf cpf = Cpf.de("529.982.247-25");
            assertThat(cpf.digitos()).isEqualTo("52998224725");
            assertThat(cpf.formatado()).isEqualTo("529.982.247-25");
            assertThat(cpf.mascarado()).isEqualTo("***.982.247-**");
            assertThat(cpf).hasToString("***.982.247-**");
        }

        @ParameterizedTest
        @ValueSource(strings = {"111.111.111-11", "529.982.247-24", "123", "abcdefghijk"})
        void recusaInvalidos(String valor) {
            assertThatThrownBy(() -> Cpf.de(valor)).isInstanceOf(DadoInvalidoException.class);
        }

        @Test
        void recusaNulo() {
            assertThatThrownBy(() -> Cpf.de(null)).isInstanceOf(DadoInvalidoException.class);
        }
    }

    @Nested
    class TelefoneTest {
        @ParameterizedTest
        @ValueSource(strings = {"(69) 99999-1234", "69999991234", "+55 69 99999-1234"})
        void normalizaParaE164(String valor) {
            assertThat(Telefone.de(valor).e164()).isEqualTo("+5569999991234");
        }

        @Test
        void mascara() {
            assertThat(Telefone.de("69999991234").mascarado()).isEqualTo("(69) *****-1234");
            assertThat(Telefone.de("69999991234")).hasToString("(69) *****-1234");
        }

        @ParameterizedTest
        @ValueSource(strings = {"6933330000", "(69) 3333-0000", "0699999912", ""})
        void recusaFixoOuIncompleto(String valor) {
            assertThatThrownBy(() -> Telefone.de(valor)).isInstanceOf(DadoInvalidoException.class);
        }
    }

    @Nested
    class EmailTest {
        @Test
        void normalizaEMascara() {
            Email email = new Email("  Carla@Exemplo.COM ");
            assertThat(email.valor()).isEqualTo("carla@exemplo.com");
            assertThat(email.mascarado()).isEqualTo("c***@exemplo.com");
            assertThat(email).hasToString("c***@exemplo.com");
        }

        @ParameterizedTest
        @ValueSource(strings = {"sem-arroba", "a@b", "a b@c.com"})
        void recusaInvalidos(String valor) {
            assertThatThrownBy(() -> new Email(valor)).isInstanceOf(DadoInvalidoException.class);
        }
    }

    @Nested
    class PeriodoTest {
        private final Instant t0 = Instant.parse("2026-10-22T12:00:00Z");

        @Test
        void semiabertoNaoSobrepoeQuandoEncosta() {
            Periodo manha = new Periodo(t0, t0.plusSeconds(1800));
            Periodo seguinte = new Periodo(t0.plusSeconds(1800), t0.plusSeconds(3600));
            assertThat(manha.sobrepoe(seguinte)).isFalse();
            assertThat(manha.sobrepoe(new Periodo(t0.plusSeconds(900), t0.plusSeconds(2700)))).isTrue();
            assertThat(manha.contem(t0)).isTrue();
            assertThat(manha.contem(t0.plusSeconds(1800))).isFalse();
        }

        @Test
        void recusaFimAntesDoInicio() {
            assertThatThrownBy(() -> new Periodo(t0, t0)).isInstanceOf(DadoInvalidoException.class);
        }
    }

    @Nested
    class DinheiroTest {
        @Test
        void comparaERecusaNegativo() {
            assertThat(new Dinheiro(500).maiorQue(new Dinheiro(100))).isTrue();
            assertThat(Dinheiro.ZERO.maiorQue(new Dinheiro(1))).isFalse();
            assertThatThrownBy(() -> new Dinheiro(-1)).isInstanceOf(DadoInvalidoException.class);
        }
    }

    @Nested
    class ExcecoesTest {
        @Test
        void carregamCodigoECategoria() {
            assertThat(new ConflitoException("x", "m").categoria()).isEqualTo(ExcecaoNegocio.Categoria.CONFLITO);
            var nf = new RecursoNaoEncontradoException("paciente");
            assertThat(nf.codigo()).isEqualTo("paciente-nao-encontrado");
            assertThat(nf.getMessage()).isEqualTo("Registro não encontrado.");
        }
    }
}
