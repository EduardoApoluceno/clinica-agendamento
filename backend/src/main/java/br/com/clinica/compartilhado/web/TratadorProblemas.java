package br.com.clinica.compartilhado.web;

import br.com.clinica.compartilhado.domain.ExcecaoNegocio;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz exceções para respostas RFC 9457 (Problem Details). Erros de validação e de protocolo
 * são tratados pela classe base do Spring; exceções de negócio, aqui.
 */
@RestControllerAdvice
public class TratadorProblemas extends ResponseEntityExceptionHandler {

    static final String BASE_TIPO = "https://clinica.local/problemas/";

    @ExceptionHandler(ExcecaoNegocio.class)
    ProblemDetail tratarNegocio(ExcecaoNegocio excecao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(statusDe(excecao.categoria()),
                excecao.getMessage());
        problema.setType(URI.create(BASE_TIPO + excecao.codigo()));
        problema.setTitle(excecao.codigo());
        anexarCorrelationId(problema);
        return problema;
    }

    static HttpStatus statusDe(ExcecaoNegocio.Categoria categoria) {
        return switch (categoria) {
            case NAO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLITO -> HttpStatus.CONFLICT;
            case REGRA_VIOLADA -> HttpStatus.UNPROCESSABLE_CONTENT;
            case ACESSO_NEGADO -> HttpStatus.FORBIDDEN;
        };
    }

    private static void anexarCorrelationId(ProblemDetail problema) {
        String correlationId = MDC.get(CorrelationIdFilter.CHAVE_MDC);
        if (correlationId != null) {
            problema.setProperty("correlationId", correlationId);
        }
    }
}
