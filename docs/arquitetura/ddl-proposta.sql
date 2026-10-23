-- =====================================================================================
-- DDL PROPOSTO — sistema de clínica (PostgreSQL 18)
-- Status: PROPOSTA (Fase 0). Será fatiado em migrações Flyway por módulo.
-- Convenções: uuid (v7, gerado na aplicação); timestamptz em UTC; clinica_id em toda tabela
-- de negócio; colunas *_cifrado = bytea AES-256-GCM (aplicação); *_hmac = HMAC-SHA256 p/ busca.
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
    version                         bigint   NOT NULL DEFAULT 0
);

CREATE TABLE feriado (
    id          uuid PRIMARY KEY,
    clinica_id  uuid NOT NULL REFERENCES clinica(id),
    data        date NOT NULL,
    descricao   varchar(120) NOT NULL,
    UNIQUE (clinica_id, data)
);

-- ------------------------------------------------------------------ profissionais
CREATE TABLE especialidade (
    id          uuid PRIMARY KEY,
    clinica_id  uuid NOT NULL REFERENCES clinica(id),
    nome        varchar(120) NOT NULL,
    UNIQUE (clinica_id, nome)
);

CREATE TABLE profissional (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    usuario_sub         varchar(64) UNIQUE,              -- subject do Keycloak
    nome                varchar(160) NOT NULL,
    conselho            varchar(10)  NOT NULL,           -- CRM, CRP, CRO...
    numero_conselho     varchar(20)  NOT NULL,
    uf_conselho         char(2)      NOT NULL,
    ativo               boolean      NOT NULL DEFAULT true,
    criado_em           timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    UNIQUE (clinica_id, conselho, numero_conselho, uf_conselho)
);
CREATE INDEX ix_profissional_clinica ON profissional (clinica_id) WHERE ativo;

CREATE TABLE profissional_especialidade (
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    especialidade_id    uuid NOT NULL REFERENCES especialidade(id),
    PRIMARY KEY (profissional_id, especialidade_id)
);

CREATE TABLE tipo_servico (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    nome                varchar(120) NOT NULL,
    descricao           text,
    instrucoes_preparo  text,
    duracao             interval     NOT NULL CHECK (duracao > interval '0'),
    preco_centavos      bigint       NOT NULL CHECK (preco_centavos >= 0),
    politica_pagamento  varchar(10)  NOT NULL DEFAULT 'NENHUM'
                        CHECK (politica_pagamento IN ('NENHUM','SINAL','INTEGRAL')),
    valor_sinal_centavos bigint      CHECK (valor_sinal_centavos >= 0),
    ativo               boolean      NOT NULL DEFAULT true,
    version             bigint       NOT NULL DEFAULT 0,
    CHECK (politica_pagamento <> 'SINAL' OR valor_sinal_centavos IS NOT NULL)
);

CREATE TABLE profissional_servico (
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    tipo_servico_id     uuid NOT NULL REFERENCES tipo_servico(id),
    duracao_override    interval,
    preco_override_centavos bigint,
    PRIMARY KEY (profissional_id, tipo_servico_id)
);

-- Expediente semanal: uma linha por faixa de trabalho (intervalos = lacunas entre faixas)
CREATE TABLE expediente_faixa (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    dia_semana          smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),   -- ISO
    hora_inicio         time NOT NULL,
    hora_fim            time NOT NULL,
    vigente_desde       date NOT NULL DEFAULT current_date,
    vigente_ate         date,
    CHECK (hora_fim > hora_inicio)
);
CREATE INDEX ix_expediente_prof ON expediente_faixa (clinica_id, profissional_id, dia_semana);

CREATE TABLE ausencia (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    tipo                varchar(16) NOT NULL CHECK (tipo IN ('FERIAS','BLOQUEIO','EXTERNO_GOOGLE')),
    periodo             tstzrange   NOT NULL,
    motivo              varchar(200),
    id_evento_externo   varchar(200),
    criado_por          varchar(64) NOT NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (profissional_id, id_evento_externo)
);
CREATE INDEX ix_ausencia_periodo ON ausencia USING gist (clinica_id, profissional_id, periodo);

-- ------------------------------------------------------------------ pacientes
CREATE TABLE paciente (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    usuario_sub         varchar(64),                     -- conta Keycloak (obrigatória p/ portal)
    nome                varchar(160) NOT NULL,
    nome_social         varchar(160),
    data_nascimento     date         NOT NULL,
    cpf_cifrado         bytea        NOT NULL,           -- obrigatório (D3: NFS-e e unicidade)
    cpf_hmac            bytea        NOT NULL,
    sexo                varchar(20),
    canal_preferido     varchar(10)  NOT NULL DEFAULT 'WHATSAPP'
                        CHECK (canal_preferido IN ('WHATSAPP','EMAIL','SMS')),
    origem_cadastro     varchar(12)  NOT NULL CHECK (origem_cadastro IN ('PORTAL','WHATSAPP','RECEPCAO')),
    anonimizado_em      timestamptz,
    criado_em           timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    UNIQUE (clinica_id, cpf_hmac),
    UNIQUE (clinica_id, usuario_sub)
);

CREATE TABLE contato_paciente (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    tipo                varchar(8) NOT NULL CHECK (tipo IN ('EMAIL','CELULAR')),
    valor_cifrado       bytea NOT NULL,
    valor_hmac          bytea NOT NULL,                  -- p/ achar paciente pelo nº do WhatsApp
    principal           boolean NOT NULL DEFAULT false,
    verificado_em       timestamptz,
    UNIQUE (clinica_id, tipo, valor_hmac)
);

CREATE TABLE consentimento (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    finalidade          varchar(40) NOT NULL,            -- TRATAMENTO_SAUDE, LEMBRETES_WHATSAPP, MARKETING...
    versao_termo        varchar(20) NOT NULL,
    concedido_em        timestamptz NOT NULL,
    revogado_em         timestamptz,
    origem              varchar(12) NOT NULL,
    ip                  inet
);
CREATE INDEX ix_consentimento_paciente ON consentimento (clinica_id, paciente_id, finalidade);

-- ------------------------------------------------------------------ agenda
CREATE TABLE agendamento (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    tipo_servico_id     uuid NOT NULL REFERENCES tipo_servico(id),
    periodo             tstzrange   NOT NULL CHECK (NOT isempty(periodo) AND lower_inc(periodo) AND NOT upper_inc(periodo)),
    status              varchar(24) NOT NULL CHECK (status IN (
                            'RESERVADO','AGUARDANDO_CONFIRMACAO','CONFIRMADO','PRESENTE',
                            'REALIZADO','FALTA','CANCELADO','EXPIRADO')),
    origem              varchar(14) NOT NULL CHECK (origem IN ('PORTAL','WHATSAPP','RECEPCAO','LISTA_ESPERA','ASSISTENTE_PORTAL')),
    hold_expira_em      timestamptz,
    preco_centavos      bigint      NOT NULL,
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

-- Preparação p/ salas/equipamentos/teleconsulta (não usada agora)
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
    -- futura EXCLUDE por recurso_id + periodo (desnormalizado) quando o recurso entrar no escopo
);

CREATE TABLE historico_agendamento (
    id              uuid PRIMARY KEY,
    clinica_id      uuid NOT NULL,
    agendamento_id  uuid NOT NULL REFERENCES agendamento(id),
    status_anterior varchar(24),
    status_novo     varchar(24) NOT NULL,
    periodo_anterior tstzrange,
    periodo_novo    tstzrange,
    ator            varchar(64) NOT NULL,
    motivo          varchar(200),
    ocorrido_em     timestamptz NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------------ prontuario
CREATE TABLE modelo_prontuario (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    especialidade_id    uuid REFERENCES especialidade(id),
    tipo_registro       varchar(20) NOT NULL,
    nome                varchar(120) NOT NULL,
    esquema             jsonb NOT NULL,                  -- campos/seções do formulário
    ativo               boolean NOT NULL DEFAULT true,
    version             bigint NOT NULL DEFAULT 0
);

CREATE TABLE registro_clinico (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    agendamento_id      uuid REFERENCES agendamento(id),
    modelo_id           uuid REFERENCES modelo_prontuario(id),
    tipo                varchar(20) NOT NULL CHECK (tipo IN
                        ('ANAMNESE','EVOLUCAO','PRESCRICAO','ATESTADO','DECLARACAO','SOLICITACAO_EXAME')),
    status              varchar(12) NOT NULL CHECK (status IN ('RASCUNHO','FINALIZADO')),
    conteudo_cifrado    bytea NOT NULL,
    hash_conteudo       bytea,                           -- sha256 do conteúdo em claro, fixado na finalização
    assinatura          jsonb,                           -- {tipo: SIMULADA|ICP_BRASIL, certificado, carimbo...}
    finalizado_em       timestamptz,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,
    CHECK (status <> 'FINALIZADO' OR (finalizado_em IS NOT NULL AND hash_conteudo IS NOT NULL))
);
CREATE INDEX ix_registro_paciente ON registro_clinico (clinica_id, paciente_id, criado_em DESC);

CREATE TABLE adendo (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL,
    registro_id         uuid NOT NULL REFERENCES registro_clinico(id),
    versao              int  NOT NULL,
    profissional_id     uuid NOT NULL REFERENCES profissional(id),
    conteudo_cifrado    bytea NOT NULL,
    hash_conteudo       bytea NOT NULL,
    assinatura          jsonb,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (registro_id, versao)
);

CREATE TABLE anexo (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL,
    registro_id         uuid NOT NULL REFERENCES registro_clinico(id),
    chave_objeto        varchar(80) NOT NULL UNIQUE,     -- nome aleatório no bucket
    nome_original_cifrado bytea NOT NULL,
    mime_detectado      varchar(80) NOT NULL,            -- Apache Tika, não o header do upload
    tamanho_bytes       bigint NOT NULL CHECK (tamanho_bytes <= 20971520),
    sha256              bytea NOT NULL,
    enviado_por         varchar(64) NOT NULL,
    criado_em           timestamptz NOT NULL DEFAULT now()
);

-- Imutabilidade: nada de DELETE; UPDATE só enquanto RASCUNHO
CREATE FUNCTION fn_registro_clinico_imutavel() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Registros de prontuário não podem ser excluídos' USING ERRCODE = 'P0001';
    END IF;
    IF OLD.status = 'FINALIZADO' THEN
        RAISE EXCEPTION 'Registro % finalizado é imutável; use adendo', OLD.id USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_registro_clinico_imutavel BEFORE UPDATE OR DELETE ON registro_clinico
    FOR EACH ROW EXECUTE FUNCTION fn_registro_clinico_imutavel();

CREATE FUNCTION fn_somente_insercao() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Tabela % é somente-inserção', TG_TABLE_NAME USING ERRCODE = 'P0001';
END $$;
CREATE TRIGGER trg_adendo_imutavel BEFORE UPDATE OR DELETE ON adendo
    FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_anexo_imutavel BEFORE UPDATE OR DELETE ON anexo
    FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();

-- ------------------------------------------------------------------ pagamentos / fiscal
CREATE TABLE cobranca (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    agendamento_id      uuid NOT NULL REFERENCES agendamento(id),
    tipo                varchar(10) NOT NULL CHECK (tipo IN ('SINAL','INTEGRAL')),
    metodo              varchar(10) CHECK (metodo IN ('PIX','CARTAO','BOLETO')),
    valor_centavos      bigint NOT NULL CHECK (valor_centavos > 0),
    status              varchar(14) NOT NULL CHECK (status IN
                        ('PENDENTE','CONFIRMADA','RECUSADA','CANCELADA','ESTORNADA','ESTORNO_PARCIAL')),
    gateway             varchar(12) NOT NULL CHECK (gateway IN ('SIMULADOR','ASAAS')),
    id_externo          varchar(64),
    pix_copia_cola      text,
    vence_em            timestamptz NOT NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,
    UNIQUE (gateway, id_externo)
);
CREATE UNIQUE INDEX ux_cobranca_ativa ON cobranca (agendamento_id) WHERE status IN ('PENDENTE','CONFIRMADA');

CREATE TABLE reembolso (
    id              uuid PRIMARY KEY,
    clinica_id      uuid NOT NULL,
    cobranca_id     uuid NOT NULL REFERENCES cobranca(id),
    valor_centavos  bigint NOT NULL CHECK (valor_centavos > 0),
    status          varchar(12) NOT NULL,
    id_externo      varchar(64),
    criado_em       timestamptz NOT NULL DEFAULT now()
);

-- Idempotência de webhooks (Asaas, Meta, Google)
CREATE TABLE webhook_recebido (
    origem          varchar(12) NOT NULL,
    id_evento       varchar(128) NOT NULL,
    recebido_em     timestamptz NOT NULL DEFAULT now(),
    processado_em   timestamptz,
    payload_hash    bytea NOT NULL,
    PRIMARY KEY (origem, id_evento)
);

CREATE TABLE nota_fiscal (
    id              uuid PRIMARY KEY,
    clinica_id      uuid NOT NULL REFERENCES clinica(id),
    cobranca_id     uuid NOT NULL REFERENCES cobranca(id),
    status          varchar(14) NOT NULL CHECK (status IN ('SOLICITADA','EMITIDA','ERRO','CANCELADA')),
    numero          varchar(30),
    link_pdf        text,
    id_externo      varchar(64),
    erro            text,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (cobranca_id)
);

-- ------------------------------------------------------------------ lista de espera
CREATE TABLE entrada_lista_espera (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid NOT NULL REFERENCES paciente(id),
    tipo_servico_id     uuid NOT NULL REFERENCES tipo_servico(id),
    profissional_id     uuid REFERENCES profissional(id),  -- null = qualquer
    janela_preferida    tstzrange,                         -- null = qualquer horário
    prioridade          smallint NOT NULL DEFAULT 0,
    status              varchar(12) NOT NULL CHECK (status IN ('ATIVA','ATENDIDA','CANCELADA')),
    criado_em           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_lista_espera_fila ON entrada_lista_espera (clinica_id, tipo_servico_id, prioridade DESC, criado_em)
    WHERE status = 'ATIVA';

CREATE TABLE oferta_vaga (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL,
    entrada_id          uuid NOT NULL REFERENCES entrada_lista_espera(id),
    profissional_id     uuid NOT NULL,
    periodo             tstzrange NOT NULL,
    status              varchar(10) NOT NULL CHECK (status IN ('OFERTADA','ACEITA','RECUSADA','EXPIRADA')),
    expira_em           timestamptz NOT NULL,
    agendamento_id      uuid REFERENCES agendamento(id),
    criado_em           timestamptz NOT NULL DEFAULT now(),
    -- uma oferta pendente por slot por vez
    CONSTRAINT oferta_unica_por_slot EXCLUDE USING gist (
        profissional_id WITH =, periodo WITH &&) WHERE (status = 'OFERTADA')
);

-- ------------------------------------------------------------------ notificacoes
CREATE TABLE template_mensagem (
    id              uuid PRIMARY KEY,
    clinica_id      uuid NOT NULL REFERENCES clinica(id),
    codigo          varchar(40) NOT NULL,             -- CONFIRMACAO, LEMBRETE_24H, OFERTA_VAGA...
    canal           varchar(10) NOT NULL,
    nome_template_meta varchar(80),                   -- template "utility" aprovado
    idioma          varchar(10) NOT NULL DEFAULT 'pt_BR',
    corpo           text NOT NULL,
    ativo           boolean NOT NULL DEFAULT true,
    UNIQUE (clinica_id, codigo, canal)
);

CREATE TABLE notificacao (
    id                  uuid PRIMARY KEY,
    clinica_id          uuid NOT NULL REFERENCES clinica(id),
    paciente_id         uuid REFERENCES paciente(id),
    agendamento_id      uuid REFERENCES agendamento(id),
    canal               varchar(10) NOT NULL,
    template_codigo     varchar(40) NOT NULL,
    destino_mascarado   varchar(40) NOT NULL,
    status              varchar(12) NOT NULL CHECK (status IN
                        ('PENDENTE','ENVIADA','ENTREGUE','LIDA','FALHOU','DESCARTADA')),
    tentativas          smallint NOT NULL DEFAULT 0,
    proxima_tentativa_em timestamptz,
    id_externo          varchar(128),
    custo_estimado_centavos int,
    categoria_meta      varchar(20),                    -- UTILITY | SERVICE (janela 24h)...
    erro                text,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_notificacao_retry ON notificacao (proxima_tentativa_em) WHERE status = 'PENDENTE';

CREATE TABLE janela_conversa_whatsapp (            -- janela de 24h iniciada pelo paciente
    clinica_id      uuid NOT NULL,
    telefone_hmac   bytea NOT NULL,
    aberta_ate      timestamptz NOT NULL,
    PRIMARY KEY (clinica_id, telefone_hmac)
);

-- ------------------------------------------------------------------ integracoes-calendario
CREATE TABLE integracao_google (
    profissional_id     uuid PRIMARY KEY REFERENCES profissional(id),
    clinica_id          uuid NOT NULL,
    refresh_token_cifrado bytea NOT NULL,
    calendario_id       varchar(200) NOT NULL,
    canal_id            varchar(64),
    canal_expira_em     timestamptz,
    sync_token          text,
    ativo               boolean NOT NULL DEFAULT true
);

-- ------------------------------------------------------------------ assistente-ia
CREATE TABLE conversa_ia (
    id              uuid PRIMARY KEY,
    clinica_id      uuid NOT NULL,
    paciente_id     uuid REFERENCES paciente(id),
    canal           varchar(10) NOT NULL CHECK (canal IN ('WHATSAPP','PORTAL')),
    status          varchar(14) NOT NULL CHECK (status IN ('ATIVA','ENCERRADA','TRANSFERIDA')),
    tokens_entrada  int NOT NULL DEFAULT 0,
    tokens_saida    int NOT NULL DEFAULT 0,
    custo_estimado_micros bigint NOT NULL DEFAULT 0,
    iniciada_em     timestamptz NOT NULL DEFAULT now(),
    encerrada_em    timestamptz
);
CREATE TABLE mensagem_ia (
    id              uuid PRIMARY KEY,
    conversa_id     uuid NOT NULL REFERENCES conversa_ia(id),
    papel           varchar(10) NOT NULL CHECK (papel IN ('PACIENTE','ASSISTENTE','FERRAMENTA','HUMANO')),
    conteudo_cifrado bytea NOT NULL,
    ferramenta      varchar(60),
    criado_em       timestamptz NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------------ auditoria (append-only)
CREATE TABLE evento_auditoria (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    clinica_id      uuid,
    ocorrido_em     timestamptz NOT NULL DEFAULT now(),
    ator_sub        varchar(64),                      -- null = sistema
    ator_papel      varchar(20),
    acao            varchar(60) NOT NULL,             -- PRONTUARIO_LEITURA, AGENDAMENTO_CANCELADO...
    recurso_tipo    varchar(40) NOT NULL,
    recurso_id      varchar(64),
    paciente_id     uuid,                             -- p/ relatório "quem acessou meus dados"
    ip              inet,
    user_agent      varchar(300),
    correlation_id  varchar(64),
    detalhes        jsonb,                            -- nunca conteúdo clínico
    hash_anterior   bytea,
    hash            bytea NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_auditoria_recurso ON evento_auditoria (recurso_tipo, recurso_id, ocorrido_em);
CREATE INDEX ix_auditoria_paciente ON evento_auditoria (paciente_id, ocorrido_em) WHERE paciente_id IS NOT NULL;
CREATE TRIGGER trg_auditoria_imutavel BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();

-- ------------------------------------------------------------------ infraestrutura
-- event_publication (Spring Modulith JDBC) e scheduled_tasks (db-scheduler) são criadas
-- pelos scripts oficiais de cada biblioteca, versionados no Flyway.

-- ------------------------------------------------------------------ privilégios
-- Executado pelo bootstrap do banco (não pelo Flyway da app):
--   clinica_migracao  = dono do schema
--   clinica_app       = SELECT/INSERT/UPDATE nas tabelas; sem DELETE em prontuário/auditoria;
--                       REVOKE UPDATE em evento_auditoria, adendo, anexo.
