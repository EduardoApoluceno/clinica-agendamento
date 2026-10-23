package br.com.clinica.pacientes.infrastructure;

import br.com.clinica.compartilhado.infrastructure.criptografia.CifradorCampos;
import br.com.clinica.pacientes.application.PacientesPortas.ProtetorCodigos;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Códigos de 6 dígitos com gerador criptográfico; guardados como HMAC e comparados em tempo constante. */
@Component
class ProtetorCodigosHmac implements ProtetorCodigos {

    private static final int LIMITE = 1_000_000;

    private final SecureRandom aleatorio = new SecureRandom();
    private final CifradorCampos cifrador;

    ProtetorCodigosHmac(CifradorCampos cifrador) {
        this.cifrador = cifrador;
    }

    @Override
    public String gerar() {
        return String.format("%06d", aleatorio.nextInt(LIMITE));
    }

    @Override
    public byte[] proteger(String codigo) {
        return cifrador.hmac("codigo-verificacao:" + codigo);
    }

    @Override
    public boolean confere(String codigoInformado, byte[] protegido) {
        return codigoInformado.matches("\\d{6}") && MessageDigest.isEqual(proteger(codigoInformado), protegido);
    }
}
