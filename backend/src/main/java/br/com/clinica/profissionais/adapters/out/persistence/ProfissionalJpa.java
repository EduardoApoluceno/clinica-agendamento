package br.com.clinica.profissionais.adapters.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProfissionalJpa extends JpaRepository<ProfissionalEntity, UUID> {
    Optional<ProfissionalEntity> findByClinicaIdAndId(UUID clinicaId, UUID id);

    Optional<ProfissionalEntity> findByClinicaIdAndUsuarioSub(UUID clinicaId, String usuarioSub);

    List<ProfissionalEntity> findByClinicaIdOrderByNome(UUID clinicaId);

    List<ProfissionalEntity> findByClinicaIdAndAtivoTrueOrderByNome(UUID clinicaId);
}
