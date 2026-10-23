package br.com.clinica.compartilhado.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propaga (ou gera) o correlation-id de cada requisição para os logs (MDC) e para a resposta.
 * Valores recebidos fora do formato esperado são descartados para evitar injeção em logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CHAVE_MDC = "correlation_id";
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = resolver(request.getHeader(HEADER));
        MDC.put(CHAVE_MDC, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }

    static String resolver(String recebido) {
        if (recebido != null && FORMATO_VALIDO.matcher(recebido).matches()) {
            return recebido;
        }
        return UUID.randomUUID().toString();
    }
}
