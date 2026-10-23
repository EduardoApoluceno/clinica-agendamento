package br.com.clinica.compartilhado.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ClinicaIdTest {

    @Test
    void criaAPartirDeTextoEPreservaValor() {
        UUID uuid = UUID.randomUUID();
        assertThat(ClinicaId.de(uuid.toString()).valor()).isEqualTo(uuid);
        assertThat(new ClinicaId(uuid)).hasToString(uuid.toString());
    }

    @Test
    void recusaValorNulo() {
        assertThatNullPointerException().isThrownBy(() -> new ClinicaId(null));
    }
}
