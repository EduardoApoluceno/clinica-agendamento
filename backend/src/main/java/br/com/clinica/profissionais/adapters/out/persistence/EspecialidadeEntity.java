package br.com.clinica.profissionais.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "especialidade")
class EspecialidadeEntity {

    @Id
    UUID id;

    @Column(name = "clinica_id", nullable = false, updatable = false)
    UUID clinicaId;

    @Column(nullable = false)
    String nome;
}
