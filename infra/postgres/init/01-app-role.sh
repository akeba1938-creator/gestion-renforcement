#!/bin/sh
# Crée le compte applicatif PostgreSQL soumis à la Row-Level Security.
# Exécuté une seule fois par l'image postgres, à l'initialisation d'un volume vide.
# Le mot de passe vient exclusivement de la variable d'environnement ECOLE_APP_DB_PASSWORD.
set -eu

: "${ECOLE_APP_DB_PASSWORD:?ECOLE_APP_DB_PASSWORD doit être défini (voir infra/env.example)}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v app_password="$ECOLE_APP_DB_PASSWORD" <<'EOSQL'
CREATE ROLE ecole_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
CREATE ROLE ecole_app_login LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE
    PASSWORD :'app_password'
    IN ROLE ecole_app;
EOSQL
