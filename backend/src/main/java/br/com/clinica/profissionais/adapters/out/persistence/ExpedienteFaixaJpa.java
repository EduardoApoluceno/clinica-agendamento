package br.com.clinica.profissionais.adapters.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ExpedienteFaixaJpa extends JpaRepository<ExpedienteFaixaEntity, UUID> {
    List<ExpedienteFaixaEntity> findByClinicaIdAndProfissionalIdOrderByDiaSemanaAscHoraInicioAsc(
            UUID clinicaId, UUID profissionalId);

    @Modifying
    @Query("DELETE FROM ExpedienteFaixaEntity f WHERE f.clinicaId = ?1 AND f.profissionalId = ?2")
    void removerDoProfissional(UUID clinicaId, UUID profissionalId);
}
