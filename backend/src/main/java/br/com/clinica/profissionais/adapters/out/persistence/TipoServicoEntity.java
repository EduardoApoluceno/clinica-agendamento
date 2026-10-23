package br.com.clinica.profissionais.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "tipo_servico")
class TipoServicoEntity {

    @Id
    UUID id;

    @Column(name = "clinica_id", nullable = false, updatable = false)
    UUID clinicaId;

    @Column(nullable = false)
    String nome;

    String descricao;

    @Column(name = "instrucoes_preparo")
    String instrucoesPreparo;

    @Column(name = "duracao_minutos", nullable = false)
    int duracaoMinutos;

    @Column(name = "preco_centavos", nullable = false)
    long precoCentavos;

    @Column(name = "politica_pagamento", nullable = false)
    String politicaPagamento;

    @Column(name = "valor_sinal_centavos")
    Long valorSinalCentavos;

    @Column(nullable = false)
    boolean ativo;

    @Version
    Long version;
}
