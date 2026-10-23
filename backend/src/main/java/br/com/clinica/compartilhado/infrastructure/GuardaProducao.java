package br.com.clinica.compartilhado.infrastructure;

import br.com.clinica.compartilhado.infrastructure.criptografia.CriptografiaProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Recusa subir o perfil prod com segredos vazios ou com os valores de demonstração do compose/application.yml,
 * e com o 2º fator da equipe desligado ({@code KC_FLUXO_NAVEGADOR=browser}, ADR-0017).
 */
@Component
@Profile("prod")
final class GuardaProducao {

    GuardaProducao(Environment ambiente, CriptografiaProperties cripto) {
        var problemas = RegrasProducao.problemas(ambiente::getProperty, cripto);
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Configuração insegura no perfil prod: " + String.join("; ", problemas));
        }
    }
}
