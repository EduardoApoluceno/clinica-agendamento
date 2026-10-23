package br.com.clinica.profissionais.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TipoServicoJpa extends JpaRepository<TipoServicoEntity, UUID> {
    Optional<TipoServicoEntity> findByClinicaIdAndId(UUID clinicaId, UUID id);

    List<TipoServicoEntity> findByClinicaIdOrderByNome(UUID clinicaId);

    List<TipoServicoEntity> findByClinicaIdAndAtivoTrueOrderByNome(UUID clinicaId);

    long countByClinicaIdAndIdIn(UUID clinicaId, Collection<UUID> ids);
}
