package br.com.clinica.profissionais.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AusenciaJpa extends JpaRepository<AusenciaEntity, UUID> {
    @Query("""
            SELECT a FROM AusenciaEntity a WHERE a.clinicaId = ?1 AND a.profissionalId = ?2
            AND a.inicio < ?4 AND a.fim > ?3 ORDER BY a.inicio""")
    List<AusenciaEntity> naJanela(UUID clinicaId, UUID profissionalId, Instant de, Instant ate);

    @Modifying
    @Query("DELETE FROM AusenciaEntity a WHERE a.clinicaId = ?1 AND a.profissionalId = ?2 AND a.id = ?3")
    int remover(UUID clinicaId, UUID profissionalId, UUID id);
}
