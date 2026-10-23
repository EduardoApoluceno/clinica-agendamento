package br.com.clinica.compartilhado.infrastructure.criptografia;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CifradorCamposTest {

    private static final String K1 = "CcsE6v4KtIYoMn91+BaiPAoaMPVc+DCpXwdu/qP4X58=";
    private static final String K2 = "23zDWKrsjR+k4rhJxTnGS97I1mA80NWbWYQveTp5q2U=";
    private static final String HMAC = "Mv/gS4HkTkyVmobp+OEvobuiqozmA02knQ844Srk/74=";

    private final CifradorCampos cifrador = new CifradorCampos(new CriptografiaProperties(Map.of("k1", K1), "k1", HMAC));

    @Test
    void cifraComIvAleatorioEDecifra() {
        byte[] a = cifrador.cifrar("52998224725");
        byte[] b = cifrador.cifrar("52998224725");
        assertThat(a).isNotEqualTo(b);
        assertThat(cifrador.decifrar(a)).isEqualTo("52998224725");
    }

    @Test
    void rotacaoLeDadosDaChaveAntiga() {
        byte[] antigo = cifrador.cifrar("dado");
        var rotacionado = new CifradorCampos(new CriptografiaProperties(Map.of("k1", K1, "k2", K2), "k2", HMAC));
        assertThat(rotacionado.decifrar(antigo)).isEqualTo("dado");
        assertThat(cifrador.hmac("x")).isEqualTo(rotacionado.hmac("x"));
    }

    @Test
    void detectaAdulteracao() {
        byte[] dado = cifrador.cifrar("dado");
        dado[dado.length - 1] ^= 1;
        assertThatThrownBy(() -> cifrador.decifrar(dado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hmacDeterministico() {
        assertThat(cifrador.hmac("abc")).isEqualTo(cifrador.hmac("abc")).isNotEqualTo(cifrador.hmac("abd"));
    }

    @Test
    void recusaConfiguracaoInvalida() {
        assertThatThrownBy(() -> new CifradorCampos(new CriptografiaProperties(Map.of("k1", K1), "k9", HMAC)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradorCampos(new CriptografiaProperties(Map.of("k1", "Y3VydGE="), "k1", HMAC)))
                .isInstanceOf(IllegalStateException.class);
    }
}
