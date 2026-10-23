-- Fase 0: extensões, clínica, auditoria append-only e tabelas de infraestrutura
-- (outbox do Spring Modulith e db-scheduler).

CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------------ funções utilitárias
CREATE FUNCTION fn_somente_insercao() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Tabela % é somente-inserção', TG_TABLE_NAME USING ERRCODE = 'P0001';
END $$;

-- ------------------------------------------------------------------ clinica
CREATE TABLE clinica (
    id                  uuid PRIMARY KEY,
    nome                varchar(160) NOT NULL,
    cnpj                varchar(14)  NOT NULL,
    fuso_horario        varchar(64)  NOT NULL DEFAULT 'America/Porto_Velho',
    endereco            jsonb        NOT NULL,
    telefone            varchar(20),
    criado_em           timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0
);

CREATE TABLE configuracao_clinica (
    clinica_id                      uuid PRIMARY KEY REFERENCES clinica(id),
    antecedencia_min_cancelamento   interval NOT NULL DEFAULT interval '2 hours',
    antecedencia_min_agendamento    interval NOT NULL DEFAULT interval '1 hour',
    horizonte_agendamento           interval NOT NULL DEFAULT interval '60 days',
    minutos_hold_pagamento          int      NOT NULL DEFAULT 15 CHECK (minutos_hold_pagamento BETWEEN 5 AND 60),
    lembretes                       interval[] NOT NULL DEFAULT ARRAY[interval '24 hours', interval '2 hours'],
    prazo_aceite_lista_espera       interval NOT NULL DEFAULT interval '30 minutes',
    sms_fallback_ativo              boolean  NOT NULL DEFAULT false,
    exige_justificativa_prontuario_sem_vinculo boolean NOT NULL DEFAULT true,
    version                         bigint   NOT NULL DEFAULT 0
);

CREATE TABLE feriado (
    id          uuid PRIMARY KEY,
    clinica_id  uuid NOT NULL REFERENCES clinica(id),
    data        date NOT NULL,
    descricao   varchar(120) NOT NULL,
    UNIQUE (clinica_id, data)
);

-- ------------------------------------------------------------------ auditoria (append-only)
CREATE TABLE evento_auditoria (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id      uuid,
    ocorrido_em     timestamptz NOT NULL DEFAULT now(),
    ator_sub        varchar(64),
    ator_papel      varchar(20),
    acao            varchar(60) NOT NULL,
    recurso_tipo    varchar(40) NOT NULL,
    recurso_id      varchar(64),
    paciente_id     uuid,
    ip              inet,
    user_agent      varchar(300),
    correlation_id  varchar(64),
    detalhes        jsonb,
    hash_anterior   bytea,
    hash            bytea NOT NULL
);
CREATE INDEX ix_auditoria_recurso ON evento_auditoria (recurso_tipo, recurso_id, ocorrido_em);
CREATE INDEX ix_auditoria_paciente ON evento_auditoria (paciente_id, ocorrido_em) WHERE paciente_id IS NOT NULL;
CREATE INDEX ix_auditoria_ator ON evento_auditoria (ator_sub, ocorrido_em);
CREATE TRIGGER trg_auditoria_imutavel BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();

-- ------------------------------------------------------------------ Spring Modulith (outbox, schema v2)
CREATE TABLE event_publication (
    id                     uuid NOT NULL PRIMARY KEY,
    listener_id            text NOT NULL,
    event_type             text NOT NULL,
    serialized_event       text NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamptz
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- ------------------------------------------------------------------ db-scheduler
CREATE TABLE scheduled_tasks (
    task_name            text NOT NULL,
    task_instance        text NOT NULL,
    task_data            bytea,
    execution_time       timestamptz NOT NULL,
    picked               boolean NOT NULL,
    picked_by            text,
    last_success         timestamptz,
    last_failure         timestamptz,
    consecutive_failures int,
    last_heartbeat       timestamptz,
    version              bigint NOT NULL,
    priority             smallint,
    PRIMARY KEY (task_name, task_instance)
);
CREATE INDEX execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX priority_execution_time_idx ON scheduled_tasks (priority DESC, execution_time ASC);

-- ------------------------------------------------------------------ privilégios do role da aplicação
-- Em demo/prod o bootstrap do banco cria clinica_app (DML) separado do dono (migração).
-- Nos testes com Testcontainers o role não existe e este bloco é ignorado.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clinica_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO clinica_app;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO clinica_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON evento_auditoria FROM clinica_app;
        REVOKE ALL ON flyway_schema_history FROM clinica_app;
    END IF;
END $$;
