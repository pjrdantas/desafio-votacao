#!/bin/sh
set -eu

# O Render fornece uma URI PostgreSQL. O driver JDBC recebe o endpoint com
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

if [ -n "${RENDER_EXTERNAL_URL:-}" ] && [ -z "${MOBILE_BASE_URL:-}" ]; then
    export MOBILE_BASE_URL="$RENDER_EXTERNAL_URL"
fi

if [ "$#" -gt 0 ]; then
    exec "$@"
fi

exec java -jar /app/app.jar
