package br.com.clinica.profissionais.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "expediente_faixa")
class ExpedienteFaixaEntity {

    @Id
    UUID id;

    @Column(name = "clinica_id", nullable = false)
    UUID clinicaId;

    @Column(name = "profissional_id", nullable = false)
    UUID profissionalId;

    @Column(name = "dia_semana", nullable = false)
    short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    LocalTime horaFim;
}
