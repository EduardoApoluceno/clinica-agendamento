package br.com.clinica.pacientes.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contato_paciente")
class ContatoEntity {

    @Id
    UUID id;

    @Column(name = "clinica_id", nullable = false)
    UUID clinicaId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paciente_id")
    PacienteEntity paciente;

    @Column(nullable = false)
    String tipo;

    @Column(name = "valor_cifrado", nullable = false)
    byte[] valorCifrado;

    @Column(name = "valor_hmac", nullable = false)
    byte[] valorHmac;

    @Column(nullable = false)
    boolean principal;

    @Column(name = "verificado_em")
    Instant verificadoEm;
}
