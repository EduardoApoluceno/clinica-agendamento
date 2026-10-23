package br.com.clinica.pacientes.domain;

import br.com.clinica.compartilhado.domain.Email;
import br.com.clinica.compartilhado.domain.Telefone;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Meio de contato do paciente.
 *
 * @param valor e-mail normalizado ou celular E.164
 */
public record Contato(Tipo tipo, String valor, boolean principal, Optional<Instant> verificadoEm) {

    public enum Tipo { EMAIL, CELULAR }

    public Contato {
        Objects.requireNonNull(tipo, "tipo");
        valor = switch (tipo) {
            case EMAIL -> new Email(valor).valor();
            case CELULAR -> Telefone.de(valor).e164();
        };
        verificadoEm = verificadoEm == null ? Optional.empty() : verificadoEm;
    }

    public static Contato celular(Telefone telefone, boolean principal) {
        return new Contato(Tipo.CELULAR, telefone.e164(), principal, Optional.empty());
    }

    public static Contato email(Email email, boolean principal, Optional<Instant> verificadoEm) {
        return new Contato(Tipo.EMAIL, email.valor(), principal, verificadoEm);
    }

    public boolean verificado() {
        return verificadoEm.isPresent();
    }

    public boolean mesmoMeio(Contato outro) {
        return tipo == outro.tipo && valor.equals(outro.valor);
    }

    public Contato verificadoEm(Instant instante) {
        return new Contato(tipo, valor, principal, Optional.of(instante));
    }

    public String mascarado() {
        return tipo == Tipo.EMAIL ? new Email(valor).mascarado() : new Telefone(valor).mascarado();
    }
}
