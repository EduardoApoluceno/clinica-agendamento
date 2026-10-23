-- Módulo pacientes (dados pessoais cifrados na aplicação, ADR-0006), códigos de verificação e
-- caixa de saída de mensagens (adaptador falso do modo demonstração).

CREATE TABLE paciente (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    usuario_sub         varchar(64),
    nome                varchar(160) NOT NULL,
    nome_social         varchar(160),
    data_nascimento     date         NOT NULL,
    cpf_cifrado         bytea        NOT NULL,
    cpf_hmac            bytea        NOT NULL,
    sexo                varchar(20),
    canal_preferido     varchar(10)  NOT NULL CHECK (canal_preferido IN ('WHATSAPP','EMAIL','SMS')),
    origem_cadastro     varchar(12)  NOT NULL CHECK (origem_cadastro IN ('PORTAL','WHATSAPP','RECEPCAO')),
    anonimizado_em      timestamptz,
    criado_em           timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    UNIQUE (clinica_id, cpf_hmac),
    UNIQUE (clinica_id, usuario_sub)
);
CREATE INDEX ix_paciente_nome ON paciente (clinica_id, lower(nome) varchar_pattern_ops);

CREATE TABLE contato_paciente (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    tipo                varchar(8) NOT NULL CHECK (tipo IN ('EMAIL','CELULAR')),
    valor_cifrado       bytea NOT NULL,
    valor_hmac          bytea NOT NULL,
    principal           boolean NOT NULL DEFAULT false,
    verificado_em       timestamptz,
    UNIQUE (paciente_id, tipo, valor_hmac)
);
-- Um mesmo celular/e-mail só pode estar VERIFICADO em um paciente da clínica (vínculo do WhatsApp).
CREATE UNIQUE INDEX ux_contato_verificado ON contato_paciente (clinica_id, tipo, valor_hmac)
    WHERE verificado_em IS NOT NULL;
CREATE INDEX ix_contato_hmac ON contato_paciente (clinica_id, tipo, valor_hmac);

CREATE TABLE consentimento (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    finalidade          varchar(40) NOT NULL,
    versao_termo        varchar(20) NOT NULL,
    concedido_em        timestamptz NOT NULL,
    revogado_em         timestamptz,
    origem              varchar(12) NOT NULL,
    ip                  inet
);
CREATE UNIQUE INDEX ux_consentimento_vigente ON consentimento (paciente_id, finalidade) WHERE revogado_em IS NULL;

CREATE TABLE codigo_verificacao (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    destino_hmac        bytea NOT NULL,
    destino_cifrado     bytea NOT NULL,
    codigo_hmac         bytea NOT NULL,
    tentativas          smallint NOT NULL DEFAULT 0,
    expira_em           timestamptz NOT NULL,
    usado_em            timestamptz,
    criado_em           timestamptz NOT NULL
);
CREATE INDEX ix_codigo_paciente ON codigo_verificacao (paciente_id, criado_em DESC);

CREATE TABLE mensagem_saida (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    canal               varchar(10) NOT NULL,
    destino_mascarado   varchar(40) NOT NULL,
    conteudo            text NOT NULL,
    criado_em           timestamptz NOT NULL
);
CREATE INDEX ix_mensagem_saida ON mensagem_saida (clinica_id, criado_em DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clinica_app') THEN
        GRANT SELECT, INSERT, UPDATE ON paciente, contato_paciente, consentimento, codigo_verificacao,
            mensagem_saida TO clinica_app;
        -- Contatos podem ser removidos pelo titular; pacientes e consentimentos nunca (histórico LGPD).
        GRANT DELETE ON contato_paciente, codigo_verificacao, mensagem_saida TO clinica_app;
    END IF;
END $$;
