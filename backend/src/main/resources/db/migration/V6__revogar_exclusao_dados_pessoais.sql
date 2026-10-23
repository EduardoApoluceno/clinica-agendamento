-- O bootstrap concede DML (incl. DELETE) por padrão a tabelas novas. Pacientes e consentimentos
-- nunca são excluídos fisicamente (histórico LGPD; anonimização é um UPDATE): revogar explicitamente.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clinica_app') THEN
        REVOKE DELETE, TRUNCATE ON paciente, consentimento, clinica, configuracao_clinica, profissional,
            tipo_servico, especialidade FROM clinica_app;
    END IF;
END $$;
