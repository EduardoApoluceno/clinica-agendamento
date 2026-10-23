# ADR-0006: Criptografia de colunas sensíveis na aplicação

- Status: Proposto · Data: 2026-10-22

## Contexto
pgcrypto exige passar a chave no SQL, o que a expõe a logs e a `pg_stat_statements`. Além disso,
um backup do banco junto com a chave no mesmo lugar anula a proteção.

## Decisão
- AES-256-GCM na aplicação, com envelope (DEK por registro cifrada por uma KEK). A KEK vem de um
  Docker secret ou variável de ambiente, nunca do banco nem do repositório. Há um campo `kid` para rotação.
- Buscas exatas usam HMAC-SHA256 com uma chave própria (CPF, telefone, e-mail).
- Campos cifrados: CPF, contatos, conteúdo do prontuário e dos adendos, nome original dos anexos,
  tokens do Google, mensagens do assistente.
- Anexos: cifrados na aplicação antes de ir ao S3 (independe do armazenamento escolhido).
- Em repouso no VPS: volume cifrado (avaliar LUKS no ADR de produção) e backups cifrados (WAL-G + libsodium).
