package br.com.clinica.compartilhado.infrastructure.criptografia;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chaves vindas de variáveis de ambiente / Docker secrets — nunca do banco ou do repositório.
 *
 * @param chaves kid → chave AES-256 em Base64 (manter as antigas para ler dados ainda não recifrados)
 * @param chaveAtiva kid usado para cifrar novos dados
 * @param chaveHmac chave HMAC-SHA256 em Base64 (diferente das chaves AES)
 */
@ConfigurationProperties("app.criptografia")
public record CriptografiaProperties(Map<String, String> chaves, String chaveAtiva, String chaveHmac) {

    public CriptografiaProperties {
        chaves = chaves == null ? Map.of() : Map.copyOf(chaves);
    }
}
