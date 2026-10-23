package br.com.clinica.compartilhado.infrastructure.criptografia;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Criptografia de colunas sensíveis na aplicação (ADR-0006): AES-256-GCM com chave fora do banco e
 * identificador de chave (kid) para rotação; HMAC-SHA256 separado para buscas exatas.
 *
 * <p>Formato: {@code [versão=1][tamanho kid][kid][IV 12 bytes][cifra+tag]}.
 */
@Component
public final class CifradorCampos {

    private static final byte VERSAO = 1;
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_CHAVE = 32;
    private static final String AES = "AES";

    private final Map<String, SecretKey> chaves = new HashMap<>();
    private final String kidAtivo;
    private final SecretKeySpec chaveHmac;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorCampos(CriptografiaProperties props) {
        props.chaves().forEach((kid, base64) -> chaves.put(kid, new SecretKeySpec(decodificar(base64), AES)));
        if (!chaves.containsKey(props.chaveAtiva())) {
            throw new IllegalStateException("Chave ativa '" + props.chaveAtiva() + "' não configurada");
        }
        this.kidAtivo = props.chaveAtiva();
        this.chaveHmac = new SecretKeySpec(decodificar(props.chaveHmac()), "HmacSHA256");
    }

    public byte[] cifrar(String textoClaro) {
        byte[] kid = kidAtivo.getBytes(StandardCharsets.US_ASCII);
        byte[] iv = new byte[TAMANHO_IV];
        aleatorio.nextBytes(iv);
        byte[] cifra = executar(Cipher.ENCRYPT_MODE, chaves.get(kidAtivo), iv,
                textoClaro.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(2 + kid.length + TAMANHO_IV + cifra.length)
                .put(VERSAO).put((byte) kid.length).put(kid).put(iv).put(cifra).array();
    }

    public String decifrar(byte[] dado) {
        ByteBuffer buffer = ByteBuffer.wrap(dado);
        if (buffer.get() != VERSAO) {
            throw new IllegalStateException("Formato de dado cifrado desconhecido");
        }
        byte[] kid = new byte[buffer.get()];
        buffer.get(kid);
        byte[] iv = new byte[TAMANHO_IV];
        buffer.get(iv);
        byte[] cifra = new byte[buffer.remaining()];
        buffer.get(cifra);
        SecretKey chave = chaves.get(new String(kid, StandardCharsets.US_ASCII));
        if (chave == null) {
            throw new IllegalStateException("Chave de criptografia não disponível para o dado");
        }
        return new String(executar(Cipher.DECRYPT_MODE, chave, iv, cifra), StandardCharsets.UTF_8);
    }

    /** Impressão digital determinística para busca exata/unicidade sem expor o valor. */
    public byte[] hmac(String valorNormalizado) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHmac);
            return mac.doFinal(valorNormalizado.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular HMAC", e);
        }
    }

    private static byte[] executar(int modo, SecretKey chave, byte[] iv, byte[] entrada) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(modo, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return cipher.doFinal(entrada);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha de criptografia (dado adulterado ou chave errada)", e);
        }
    }

    private static byte[] decodificar(String base64) {
        byte[] chave = Base64.getDecoder().decode(base64);
        if (chave.length != TAMANHO_CHAVE) {
            throw new IllegalStateException("Chaves de criptografia devem ter 256 bits (32 bytes em Base64)");
        }
        return chave;
    }
}
