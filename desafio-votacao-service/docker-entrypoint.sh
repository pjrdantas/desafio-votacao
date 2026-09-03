#!/bin/sh
set -eu

# Algumas plataformas fornecem uma URI PostgreSQL. O driver JDBC recebe o
# prefixo jdbc: e as credenciais em variáveis separadas.
if [ -n "${DATABASE_URL:-}" ] && [ -z "${DB_URL:-}" ]; then
    case "$DATABASE_URL" in
        postgres://*|postgresql://*)
            database_endpoint=${DATABASE_URL#*://}
            database_endpoint=${database_endpoint#*@}
            export DB_URL="jdbc:postgresql://${database_endpoint}"
            ;;
        *)
            echo "DATABASE_URL deve usar o protocolo PostgreSQL." >&2
            exit 1
            ;;
    esac
fi

if [ "$#" -gt 0 ]; then
    exec "$@"
fi

exec java -jar /app/app.jar
