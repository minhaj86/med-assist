# MedAssist clinician UI: static Vite build served by unprivileged nginx, which also proxies /api to the backend.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30-alpine
COPY docker/nginx/default.conf.template docker/nginx/security-headers.inc.template /etc/nginx/templates/
COPY docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --from=build /app/dist /usr/share/nginx/html
# Defaults for the local Docker Compose stack; override per environment.
ENV BACKEND_URL=http://backend:8081 \
    OIDC_ORIGIN=http://localhost:8180 \
    MEDASSIST_OIDC_AUTHORITY=http://localhost:8180/realms/medassist \
    MEDASSIST_OIDC_CLIENT_ID=medassist-web \
    MEDASSIST_KEYCLOAK_ADMIN_URL= \
    MEDASSIST_ENVIRONMENT_LABEL= \
    NGINX_ENVSUBST_FILTER="^(BACKEND_URL|OIDC_ORIGIN)$"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
