package br.com.clinica.profissionais.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EspecialidadeJpa extends JpaRepository<EspecialidadeEntity, UUID> {
    List<EspecialidadeEntity> findByClinicaIdOrderByNome(UUID clinicaId);

    long countByClinicaIdAndIdIn(UUID clinicaId, Collection<UUID> ids);

    boolean existsByClinicaIdAndNomeIgnoreCase(UUID clinicaId, String nome);
}
