#!/usr/bin/env bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

python3 "$SCRIPT_DIR/configure-local.py"

echo "==> Starting infrastructure..."
docker compose --env-file "$SCRIPT_DIR/.env" -f "$SCRIPT_DIR/docker-compose.infra.yml" up -d "$@"

echo ""
echo "Infrastructure running:"
echo "  Keycloak:   http://localhost:8180 (credentials are in the ignored .env file)"
echo "  Kafka UI:   http://localhost:9093"
echo "  PostgreSQL: localhost:5432"
echo "  Redis:      localhost:6379"
