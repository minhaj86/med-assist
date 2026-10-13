#!/bin/sh
# Writes /runtime-config.js from environment variables so one image works in every environment.
set -eu

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/</\\u003c/g'
}

mkdir -p /tmp/medassist
cat > /tmp/medassist/runtime-config.js <<JS
window.__MEDASSIST_CONFIG__ = {
  "apiBaseUrl": "/api",
  "oidcAuthority": "$(json_escape "${MEDASSIST_OIDC_AUTHORITY}")",
  "oidcClientId": "$(json_escape "${MEDASSIST_OIDC_CLIENT_ID}")",
  "keycloakAdminUrl": "$(json_escape "${MEDASSIST_KEYCLOAK_ADMIN_URL:-}")",
  "environmentLabel": "$(json_escape "${MEDASSIST_ENVIRONMENT_LABEL:-}")"
};
JS
echo "40-runtime-config.sh: wrote /tmp/medassist/runtime-config.js"
