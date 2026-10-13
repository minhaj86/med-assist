#!/bin/bash
# First-start initialisation (runs once, as the PostgreSQL superuser, when the data volume is empty).
#  - medassist_owner : owns the schema; used only by Flyway migrations (DDL)
#  - medassist_app   : runtime role used by the application (DML only; append-only tables restricted by Flyway)
#  - keycloak        : separate database for the local identity provider
set -euo pipefail

: "${MEDASSIST_DB_OWNER_PASSWORD:?MEDASSIST_DB_OWNER_PASSWORD is required}"
: "${MEDASSIST_DB_APP_PASSWORD:?MEDASSIST_DB_APP_PASSWORD is required}"
: "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v owner_pw="$MEDASSIST_DB_OWNER_PASSWORD" -v app_pw="$MEDASSIST_DB_APP_PASSWORD" -v kc_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE medassist_owner LOGIN PASSWORD :'owner_pw';
CREATE ROLE medassist_app LOGIN PASSWORD :'app_pw' CONNECTION LIMIT 50;
CREATE ROLE keycloak LOGIN PASSWORD :'kc_pw';

CREATE DATABASE medassist OWNER medassist_owner;
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE medassist FROM PUBLIC;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE medassist TO medassist_owner;
GRANT CONNECT ON DATABASE medassist TO medassist_app;

\connect medassist
-- pgvector requires superuser to install; the Flyway migration's CREATE EXTENSION IF NOT EXISTS is then a no-op.
CREATE EXTENSION IF NOT EXISTS vector;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO medassist_owner;
GRANT USAGE ON SCHEMA public TO medassist_app;
-- Tables created by migrations are readable/writable by the runtime role; Flyway's repeatable grant script then
-- revokes UPDATE/DELETE on append-only tables (audit trail, assessment versions, feedback, ...).
ALTER DEFAULT PRIVILEGES FOR ROLE medassist_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO medassist_app;
ALTER DEFAULT PRIVILEGES FOR ROLE medassist_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO medassist_app;
SQL
echo "MedAssist database roles initialised"
