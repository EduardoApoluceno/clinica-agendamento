-- Fase 2: agendamentos com proteção contra overbooking no banco (ADR-0004).

CREATE TABLE agendamento (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    tipo_servico_id     uuid NOT NULL REFERENCES tipo_servico(id),
    periodo             tstzrange   NOT NULL
                        CHECK (NOT isempty(periodo) AND lower_inc(periodo) AND NOT upper_inc(periodo)),
    status              varchar(24) NOT NULL CHECK (status IN (
                            'RESERVADO','AGUARDANDO_CONFIRMACAO','CONFIRMADO','PRESENTE',
                            'REALIZADO','FALTA','CANCELADO','EXPIRADO')),
    origem              varchar(17) NOT NULL
                        CHECK (origem IN ('PORTAL','WHATSAPP','RECEPCAO','LISTA_ESPERA','ASSISTENTE_PORTAL')),
    hold_expira_em      timestamptz,
    preco_centavos      bigint      NOT NULL CHECK (preco_centavos >= 0),
    valor_antecipado_centavos bigint NOT NULL DEFAULT 0 CHECK (valor_antecipado_centavos >= 0),
    observacao          varchar(500),
    cancelado_em        timestamptz,
    motivo_cancelamento varchar(200),
    criado_por          varchar(64) NOT NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    CHECK (status <> 'RESERVADO' OR hold_expira_em IS NOT NULL),
    -- REGRA CRÍTICA: impede overbooking inclusive entre requisições concorrentes
    CONSTRAINT agendamento_sem_sobreposicao EXCLUDE USING gist (
        clinica_id WITH =, profissional_id WITH =, periodo WITH &&
    ) WHERE (status IN ('RESERVADO','AGUARDANDO_CONFIRMACAO','CONFIRMADO','PRESENTE','REALIZADO','FALTA'))
);
CREATE INDEX ix_agendamento_paciente ON agendamento (clinica_id, paciente_id, lower(periodo) DESC);
CREATE INDEX ix_agendamento_hold     ON agendamento (hold_expira_em) WHERE status = 'RESERVADO';
CREATE INDEX ix_agendamento_dia      ON agendamento (clinica_id, lower(periodo));

-- Preparação para salas/equipamentos/teleconsulta (não usada agora)
CREATE TABLE recurso (
    id          uuid PRIMARY KEY,
    clinica_id  uuid NOT NULL REFERENCES clinica(id),
    tipo        varchar(12) NOT NULL CHECK (tipo IN ('SALA','EQUIPAMENTO','LINK_VIDEO')),
    nome        varchar(120) NOT NULL,
    ativo       boolean NOT NULL DEFAULT true
);
CREATE TABLE agendamento_recurso (
    agendamento_id  uuid NOT NULL REFERENCES agendamento(id),
    recurso_id      uuid NOT NULL REFERENCES recurso(id),
    PRIMARY KEY (agendamento_id, recurso_id)
);

-- Cada transição (criação, remarcação, cancelamento...) com quem e quando. Append-only.
CREATE TABLE historico_agendamento (
    id               uuid PRIMARY KEY,
    clinica_id       uuid NOT NULL,
    agendamento_id   uuid NOT NULL REFERENCES agendamento(id),
    status_anterior  varchar(24),
    status_novo      varchar(24) NOT NULL,
    periodo_anterior tstzrange,
    periodo_novo     tstzrange,
    ator             varchar(64) NOT NULL,
    motivo           varchar(200),
    ocorrido_em      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_historico_agendamento ON historico_agendamento (agendamento_id, ocorrido_em);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clinica_app') THEN
        GRANT SELECT, INSERT, UPDATE ON agendamento, recurso, agendamento_recurso TO clinica_app;
        GRANT SELECT, INSERT ON historico_agendamento TO clinica_app;
        -- Agendamentos nunca são excluídos (histórico e relatórios); cancelar é mudar o status.
        REVOKE DELETE, TRUNCATE ON agendamento, historico_agendamento FROM clinica_app;
        REVOKE UPDATE ON historico_agendamento FROM clinica_app;
    END IF;
END $$;
