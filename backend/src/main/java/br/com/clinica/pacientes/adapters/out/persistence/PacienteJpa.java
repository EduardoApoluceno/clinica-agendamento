package br.com.clinica.pacientes.adapters.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PacienteJpa extends JpaRepository<PacienteEntity, UUID> {

    Optional<PacienteEntity> findByClinicaIdAndId(UUID clinicaId, UUID id);

    Optional<PacienteEntity> findByClinicaIdAndUsuarioSub(UUID clinicaId, String usuarioSub);

    Optional<PacienteEntity> findByClinicaIdAndCpfHmac(UUID clinicaId, byte[] cpfHmac);

    @Query("""
            SELECT c.paciente FROM ContatoEntity c
            WHERE c.clinicaId = ?1 AND c.tipo = ?2 AND c.valorHmac = ?3 AND c.verificadoEm IS NOT NULL""")
    Optional<PacienteEntity> porContatoVerificado(UUID clinicaId, String tipo, byte[] valorHmac);

    @Query("""
            SELECT p FROM PacienteEntity p WHERE p.clinicaId = ?1 AND p.anonimizadoEm IS NULL
            AND (lower(p.nome) LIKE lower(concat('%', ?2, '%')) OR lower(p.nomeSocial) LIKE lower(concat('%', ?2, '%')))
            ORDER BY p.nome""")
    Page<PacienteEntity> porNome(UUID clinicaId, String trecho, Pageable pagina);

    @Query("""
            SELECT DISTINCT p FROM PacienteEntity p LEFT JOIN p.contatos c
            WHERE p.clinicaId = ?1 AND (p.cpfHmac = ?2 OR c.valorHmac = ?2)
            ORDER BY p.nome""")
    Page<PacienteEntity> porIdentificador(UUID clinicaId, byte[] hmac, Pageable pagina);
}
