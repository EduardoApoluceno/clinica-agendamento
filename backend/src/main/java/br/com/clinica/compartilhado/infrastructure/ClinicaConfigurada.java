package br.com.clinica.compartilhado.infrastructure;

import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.ClinicaId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Clínica única definida por configuração ({@code app.clinica.id}). */
@Component
class ClinicaConfigurada implements ContextoClinica {

    private final ClinicaId clinicaId;

    ClinicaConfigurada(@Value("${app.clinica.id}") String id) {
        this.clinicaId = ClinicaId.de(id);
    }

    @Override
    public ClinicaId atual() {
        return clinicaId;
    }
}
