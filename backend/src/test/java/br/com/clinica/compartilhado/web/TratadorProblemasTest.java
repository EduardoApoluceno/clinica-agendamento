package br.com.clinica.compartilhado.web;

import br.com.clinica.compartilhado.domain.ExcecaoNegocio;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class TratadorProblemasTest {

    private static final class HorarioIndisponivel extends ExcecaoNegocio {
        private static final long serialVersionUID = 1L;

        HorarioIndisponivel() {
            super("horario-indisponivel", Categoria.CONFLITO, "O horário escolhido não está mais disponível.");
        }
    }

    @Test
    void converteExcecaoDeNegocioEmProblemDetails() {
        ProblemDetail problema = new TratadorProblemas().tratarNegocio(new HorarioIndisponivel());

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getType()).hasToString(TratadorProblemas.BASE_TIPO + "horario-indisponivel");
        assertThat(problema.getDetail()).isEqualTo("O horário escolhido não está mais disponível.");
    }

    @Test
    void mapeiaTodasAsCategorias() {
        assertThat(TratadorProblemas.statusDe(ExcecaoNegocio.Categoria.NAO_ENCONTRADO)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(TratadorProblemas.statusDe(ExcecaoNegocio.Categoria.CONFLITO)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(TratadorProblemas.statusDe(ExcecaoNegocio.Categoria.REGRA_VIOLADA))
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(TratadorProblemas.statusDe(ExcecaoNegocio.Categoria.ACESSO_NEGADO)).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
