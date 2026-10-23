-- Hash encadeado da trilha de auditoria (ADR-0005). Calculado no banco para ser determinístico:
-- o mesmo cálculo é usado na gravação e na verificação diária da cadeia.
ALTER TABLE evento_auditoria ALTER COLUMN ocorrido_em DROP DEFAULT;

CREATE FUNCTION fn_hash_auditoria(
    p_hash_anterior bytea, p_clinica_id uuid, p_ocorrido_em timestamptz, p_ator_sub text,
    p_ator_papel text, p_acao text, p_recurso_tipo text, p_recurso_id text, p_paciente_id uuid,
    p_ip inet, p_user_agent text, p_correlation_id text, p_detalhes jsonb)
RETURNS bytea LANGUAGE sql STABLE AS $$
    SELECT digest(
        coalesce(p_hash_anterior, ''::bytea) || convert_to(concat_ws(chr(31),
            coalesce(p_clinica_id::text, ''),
            (extract(epoch FROM p_ocorrido_em) * 1000000)::bigint::text,
            coalesce(p_ator_sub, ''), coalesce(p_ator_papel, ''), p_acao, p_recurso_tipo,
            coalesce(p_recurso_id, ''), coalesce(p_paciente_id::text, ''), coalesce(host(p_ip), ''),
            coalesce(p_user_agent, ''), coalesce(p_correlation_id, ''), coalesce(p_detalhes::text, '')
        ), 'UTF8'),
        'sha256')
$$;
