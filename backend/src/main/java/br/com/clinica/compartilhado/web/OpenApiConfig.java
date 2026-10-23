package br.com.clinica.compartilhado.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import java.util.Locale;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * OpenAPI estável para geração do cliente (Orval): operationId = controller + método
 * (ex.: {@code pacientesPesquisar}) e tag = nome do controller, sem sufixos numéricos.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String SUFIXO = "Controller";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("Clínica — API").version("v1")
                .description("API do BFF. Autenticação por cookie de sessão; mutações exigem X-XSRF-TOKEN."));
    }

    @Bean
    OperationCustomizer operationIdPorController() {
        return (operacao, handler) -> {
            String recurso = recurso(handler);
            String metodo = handler.getMethod().getName();
            operacao.setOperationId(recurso + Character.toUpperCase(metodo.charAt(0)) + metodo.substring(1));
            operacao.setTags(List.of(recurso));
            return operacao;
        };
    }

    static String recurso(HandlerMethod handler) {
        String nome = handler.getBeanType().getSimpleName();
        String base = nome.endsWith(SUFIXO) ? nome.substring(0, nome.length() - SUFIXO.length()) : nome;
        return base.substring(0, 1).toLowerCase(Locale.ROOT) + base.substring(1);
    }
}
