-- Módulo profissionais: especialidades, profissionais, serviços, expediente e ausências.

CREATE TABLE especialidade (
    id          uuid PRIMARY KEY,
    clinica_id  uuid NOT NULL REFERENCES clinica(id),
    nome        varchar(120) NOT NULL,
    UNIQUE (clinica_id, nome)
);

CREATE TABLE profissional (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    usuario_sub         varchar(64),
    nome                varchar(160) NOT NULL,
    conselho            varchar(10)  NOT NULL,
    numero_conselho     varchar(20)  NOT NULL,
    uf_conselho         varchar(2)   NOT NULL CHECK (uf_conselho ~ '^[A-Z]{2}$'),
    ativo               boolean      NOT NULL DEFAULT true,
    criado_em           timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    UNIQUE (clinica_id, conselho, numero_conselho, uf_conselho),
    UNIQUE (clinica_id, usuario_sub)
);
CREATE INDEX ix_profissional_clinica ON profissional (clinica_id) WHERE ativo;

CREATE TABLE profissional_especialidade (
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    especialidade_id    uuid NOT NULL REFERENCES especialidade(id),
    PRIMARY KEY (profissional_id, especialidade_id)
);

CREATE TABLE tipo_servico (
    id                   uuid PRIMARY KEY,
    clinica_id           uuid NOT NULL REFERENCES clinica(id),
    nome                 varchar(120) NOT NULL,
    descricao            varchar(1000),
    instrucoes_preparo   varchar(2000),
    duracao_minutos      int     NOT NULL CHECK (duracao_minutos BETWEEN 5 AND 480),
    preco_centavos       bigint  NOT NULL CHECK (preco_centavos >= 0),
    politica_pagamento   varchar(10) NOT NULL CHECK (politica_pagamento IN ('NENHUM','SINAL','INTEGRAL')),
    valor_sinal_centavos bigint  CHECK (valor_sinal_centavos > 0),
    ativo                boolean NOT NULL DEFAULT true,
    version              bigint  NOT NULL DEFAULT 0,
    UNIQUE (clinica_id, nome),
    CHECK ((politica_pagamento = 'SINAL') = (valor_sinal_centavos IS NOT NULL))
);

CREATE TABLE profissional_servico (
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    tipo_servico_id     uuid NOT NULL REFERENCES tipo_servico(id),
    PRIMARY KEY (profissional_id, tipo_servico_id)
);

CREATE TABLE expediente_faixa (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    dia_semana          smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_inicio         time NOT NULL,
    hora_fim            time NOT NULL,
    CHECK (hora_fim > hora_inicio)
);
CREATE INDEX ix_expediente_prof ON expediente_faixa (clinica_id, profissional_id, dia_semana);

CREATE TABLE ausencia (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    tipo                varchar(16) NOT NULL CHECK (tipo IN ('FERIAS','BLOQUEIO','EXTERNO_GOOGLE')),
    inicio              timestamptz NOT NULL,
    fim                 timestamptz NOT NULL,
    periodo             tstzrange GENERATED ALWAYS AS (tstzrange(inicio, fim, '[)')) STORED,
    motivo              varchar(200),
    id_evento_externo   varchar(200),
    criado_por          varchar(64) NOT NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    CHECK (fim > inicio),
    UNIQUE (profissional_id, id_evento_externo)
);
CREATE INDEX ix_ausencia_periodo ON ausencia USING gist (clinica_id, profissional_id, periodo);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clinica_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON especialidade, profissional, profissional_especialidade,
            tipo_servico, profissional_servico, expediente_faixa, ausencia TO clinica_app;
    END IF;
END $$;
