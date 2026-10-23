package br.com.clinica.profissionais.adapters.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "profissional")
class ProfissionalEntity {

    @Id
    UUID id;

    @Column(name = "clinica_id", nullable = false, updatable = false)
    UUID clinicaId;

    @Column(name = "usuario_sub")
    String usuarioSub;

    @Column(nullable = false)
    String nome;

    @Column(nullable = false)
    String conselho;

    @Column(name = "numero_conselho", nullable = false)
    String numeroConselho;

    @Column(name = "uf_conselho", nullable = false)
    String ufConselho;

    @Column(nullable = false)
    boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profissional_especialidade", joinColumns = @JoinColumn(name = "profissional_id"))
    @Column(name = "especialidade_id")
    Set<UUID> especialidades = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profissional_servico", joinColumns = @JoinColumn(name = "profissional_id"))
    @Column(name = "tipo_servico_id")
    Set<UUID> servicos = new HashSet<>();

    @Version
    Long version;
}
