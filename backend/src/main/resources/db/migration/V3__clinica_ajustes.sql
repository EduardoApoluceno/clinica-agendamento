-- Lembretes em minutos (inteiros) em vez de interval[]: mais simples de mapear e validar.
ALTER TABLE configuracao_clinica DROP COLUMN lembretes;
ALTER TABLE configuracao_clinica ADD COLUMN lembretes_minutos int[] NOT NULL DEFAULT ARRAY[1440, 120];
ALTER TABLE configuracao_clinica ADD CONSTRAINT ck_lembretes_positivos
    CHECK (0 < ALL (lembretes_minutos) AND cardinality(lembretes_minutos) <= 5);

ALTER TABLE clinica ADD CONSTRAINT ck_clinica_cnpj CHECK (cnpj ~ '^\d{14}$');
