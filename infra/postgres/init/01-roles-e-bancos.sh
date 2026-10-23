#!/bin/sh
# Executado apenas na PRIMEIRA inicialização do volume do PostgreSQL.
# Cria: dono do schema (migrações), role da aplicação (somente DML) e banco do Keycloak.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
     -v senha_migracao="$DB_MIGRATION_PASSWORD" \
     -v senha_app="$DB_APP_PASSWORD" \
     -v senha_kc="$KC_DB_PASSWORD" <<'EOSQL'
CREATE ROLE clinica_migracao LOGIN PASSWORD :'senha_migracao';
CREATE ROLE clinica_app LOGIN PASSWORD :'senha_app';
CREATE DATABASE clinica OWNER clinica_migracao;
CREATE ROLE keycloak LOGIN PASSWORD :'senha_kc';
CREATE DATABASE keycloak OWNER keycloak;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname clinica <<'EOSQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO clinica_migracao;
GRANT USAGE ON SCHEMA public TO clinica_app;
ALTER DEFAULT PRIVILEGES FOR ROLE clinica_migracao IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO clinica_app;
ALTER DEFAULT PRIVILEGES FOR ROLE clinica_migracao IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO clinica_app;
ALTER DEFAULT PRIVILEGES FOR ROLE clinica_migracao IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO clinica_app;
EOSQL
