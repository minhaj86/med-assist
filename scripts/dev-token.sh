#!/usr/bin/env bash
# LOCAL DEVELOPMENT ONLY: prints an access token for a demo user via the medassist-dev-cli client.
# Usage: TOKEN=$(scripts/dev-token.sh dr.demo) && curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/me
set -euo pipefail

cd "$(dirname "$0")/.."
USERNAME="${1:-dr.demo}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
PASSWORD="${MEDASSIST_DEMO_USER_PASSWORD:-$(grep '^MEDASSIST_DEMO_USER_PASSWORD=' .env | cut -d= -f2)}"

curl -sf -X POST "$KEYCLOAK_URL/realms/medassist/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=medassist-dev-cli -d scope=openid \
  --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
