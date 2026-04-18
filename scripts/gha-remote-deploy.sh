#!/usr/bin/env bash
# Executado no servidor via: ssh ... 'exports...; bash -s' < scripts/gha-remote-deploy.sh
# Variáveis obrigatórias: IMAGE, GHCR_USER, GHCR_TOKEN, DEPLOY_PATH
# Opcionais: APP_PORT_GH, PUBLIC_HOST_GH

set -euo pipefail

if [ -z "${DEPLOY_PATH:-}" ]; then
  echo "DEPLOY_PATH não definido." >&2
  exit 1
fi
if [ -z "${IMAGE:-}" ]; then
  echo "IMAGE não definido." >&2
  exit 1
fi
if [ -z "${GHCR_USER:-}" ] || [ -z "${GHCR_TOKEN:-}" ]; then
  echo "GHCR_USER/GHCR_TOKEN não definidos." >&2
  exit 1
fi

cd "${DEPLOY_PATH}"

echo "DEPLOY_PATH=${DEPLOY_PATH}"
ls -la "${DEPLOY_PATH}" || true

if [ ! -f ".env" ]; then
  echo "Arquivo .env não encontrado em ${DEPLOY_PATH}" >&2
  exit 1
fi
if [ ! -f "docker-compose.deploy.yml" ]; then
  echo "docker-compose.deploy.yml não encontrado em ${DEPLOY_PATH}" >&2
  exit 1
fi

if grep -q '^DEPLOY_IMAGE=' .env; then
  sed -i.bak "s#^DEPLOY_IMAGE=.*#DEPLOY_IMAGE=${IMAGE}#" .env
else
  echo "DEPLOY_IMAGE=${IMAGE}" >> .env
fi

echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USER}" --password-stdin

COMPOSE_FILES="-f docker-compose.deploy.yml"
COMPOSE_PROFILES=""
if [ -n "${PUBLIC_HOST_GH:-}" ] && [ -f "docker-compose.caddy.yml" ]; then
  COMPOSE_FILES="${COMPOSE_FILES} -f docker-compose.caddy.yml"
  COMPOSE_PROFILES="--profile https"
fi

# shellcheck disable=SC2086
docker compose ${COMPOSE_FILES} ${COMPOSE_PROFILES} pull app
# shellcheck disable=SC2086
docker compose ${COMPOSE_FILES} ${COMPOSE_PROFILES} up -d

APP_PORT_VAL="${APP_PORT_GH:-}"
if [ -z "${APP_PORT_VAL}" ]; then
  APP_PORT_VAL="$(grep -E '^APP_PORT=' .env | head -n1 | cut -d'=' -f2)"
fi
APP_PORT_VAL="${APP_PORT_VAL:-3000}"

APPLIED_IMAGE="$(docker inspect -f '{{.Config.Image}}' gear-lead-engine-app)"
echo "Imagem aplicada no container: ${APPLIED_IMAGE}"
if [ "${APPLIED_IMAGE}" != "${IMAGE}" ]; then
  echo "Imagem aplicada difere da esperada. Esperado: ${IMAGE}" >&2
  exit 1
fi

ok=0
for _ in $(seq 1 20); do
  if curl -fsS "http://127.0.0.1:${APP_PORT_VAL}/health" >/dev/null; then
    ok=1
    break
  fi
  sleep 3
done
if [ "${ok}" -ne 1 ]; then
  echo "Healthcheck HTTP interno falhou em http://127.0.0.1:${APP_PORT_VAL}/health" >&2
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} ${COMPOSE_PROFILES} ps
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} ${COMPOSE_PROFILES} logs --tail=120 app
  exit 1
fi
echo "Healthcheck interno OK."

if [ -n "${PUBLIC_HOST_GH:-}" ]; then
  ok_ext=0
  for _ in $(seq 1 20); do
    if curl -fsS "https://${PUBLIC_HOST_GH}/health" >/dev/null; then
      ok_ext=1
      break
    fi
    sleep 3
  done
  if [ "${ok_ext}" -ne 1 ]; then
    echo "Healthcheck HTTPS externo falhou em https://${PUBLIC_HOST_GH}/health" >&2
    exit 1
  fi
  echo "Healthcheck HTTPS externo OK."
fi
