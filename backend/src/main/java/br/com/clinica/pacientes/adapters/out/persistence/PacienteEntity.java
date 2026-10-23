package br.com.clinica.pacientes.adapters.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** CPF e contatos chegam aqui já cifrados (+ HMAC para busca); nome fica em claro para pesquisa. */
@Entity
@Table(name = "paciente")
class PacienteEntity {

    @Id
    UUID id;

    @Column(name = "clinica_id", nullable = false, updatable = false)
    UUID clinicaId;

    @Column(name = "usuario_sub")
    String usuarioSub;

    @Column(nullable = false)
    String nome;

    @Column(name = "nome_social")
    String nomeSocial;

    @Column(name = "data_nascimento", nullable = false)
    LocalDate dataNascimento;

    @Column(name = "cpf_cifrado", nullable = false)
    byte[] cpfCifrado;

    @Column(name = "cpf_hmac", nullable = false)
    byte[] cpfHmac;

    String sexo;

    @Column(name = "canal_preferido", nullable = false)
    String canalPreferido;

    @Column(name = "origem_cadastro", nullable = false, updatable = false)
    String origemCadastro;

    @Column(name = "anonimizado_em")
    Instant anonimizadoEm;

    @OneToMany(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    List<ContatoEntity> contatos = new ArrayList<>();

    @Version
    Long version;
}
