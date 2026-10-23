# Realm de produção

Coloque aqui o `clinica-realm.json` de produção (gerado na fase de produção):

- sem usuários de teste;
- MFA obrigatório para a equipe;
- SMTP real;
- `redirectUris` com o domínio real.

O compose monta este diretório quando `KC_REALM_DIR=realm-prod`.
