package br.com.clinica.profissionais.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A coluna gerada {@code periodo} (tstzrange) não é mapeada: existe só para o índice GiST. */
@Entity
@Table(name = "ausencia")
class AusenciaEntity {

    @Id
    UUID id;

    @Column(name = "clinica_id", nullable = false)
    UUID clinicaId;

    @Column(name = "profissional_id", nullable = false)
    UUID profissionalId;

    @Column(nullable = false)
    String tipo;

    @Column(nullable = false)
    Instant inicio;

    @Column(nullable = false)
    Instant fim;

    String motivo;

    @Column(name = "criado_por", nullable = false)
    String criadoPor;
}
