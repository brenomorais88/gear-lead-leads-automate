#!/usr/bin/env bash
# Bootstrap Gear Lead Engine em droplet Ubuntu (DigitalOcean).
#
# Modo 1 — build no servidor (pesado):
#   bash scripts/bootstrap-do-droplet.sh root@IP
#
# Modo 2 — só imagem da esteira (GHCR), sem build no servidor (recomendado):
#   DEPLOY_IMAGE=ghcr.io/dono/repo:integracao_whats bash scripts/bootstrap-do-droplet.sh root@IP
#   Opcional pacote privado: GHCR_PULL_TOKEN=ghp_... GHCR_USER=githubuser (mesmo modo)
#   Manter pasta/dados existentes: SKIP_CLONE=1
#
# Opcional: APP_PORT=3847
# HTTPS (Caddy + Let's Encrypt): defina PUBLIC_HOST=webhook.seudominio.com.br (DNS A → IP do servidor; 80/443 abertos).

set -euo pipefail

HOST="${1:?Informe o host SSH, ex: root@64.23.175.51}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCAL_ENV="${LOCAL_ENV:-$REPO_ROOT/.env}"
REMOTE_DIR="${REMOTE_DIR:-/opt/gear-lead-leads-automate}"
GIT_URL="${GIT_URL:-https://github.com/brenomorais88/gear-lead-leads-automate.git}"
GIT_BRANCH="${GIT_BRANCH:-integracao_whats}"
DEPLOY_COMPOSE_SRC="${DEPLOY_COMPOSE_SRC:-$REPO_ROOT/docker-compose.deploy.yml}"
CADDY_COMPOSE_SRC="${CADDY_COMPOSE_SRC:-$REPO_ROOT/docker-compose.caddy.yml}"
CADDYFILE_SRC="${CADDYFILE_SRC:-$REPO_ROOT/Caddyfile}"

if [[ ! -f "$LOCAL_ENV" ]]; then
  echo "Arquivo não encontrado: $LOCAL_ENV" >&2
  exit 1
fi

if [[ -n "${DEPLOY_IMAGE:-}" ]] && [[ ! -f "$DEPLOY_COMPOSE_SRC" ]]; then
  echo "Arquivo não encontrado: $DEPLOY_COMPOSE_SRC" >&2
  exit 1
fi

if [[ -n "${PUBLIC_HOST:-}" ]]; then
  if [[ ! -f "$CADDY_COMPOSE_SRC" ]] || [[ ! -f "$CADDYFILE_SRC" ]]; then
    echo "HTTPS: faltam $CADDY_COMPOSE_SRC ou $CADDYFILE_SRC" >&2
    exit 1
  fi
fi

ssh_base=(ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=45 -o ServerAliveInterval=15 -o ServerAliveCountMax=8 "$HOST")

echo "==> Testando SSH para $HOST ..."
"${ssh_base[@]}" "echo ssh_ok"

pick_port() {
  local start="${1:-3847}"
  local p
  for p in $(seq "$start" $((start + 50))); do
    if "${ssh_base[@]}" "ss -tln 2>/dev/null | grep -qE ':${p}\\s'"; then
      continue
    fi
    echo "$p"
    return 0
  done
  echo "Nenhuma porta livre no intervalo" >&2
  return 1
}

if [[ -n "${APP_PORT:-}" ]]; then
  PORT="$APP_PORT"
  echo "==> Usando porta fixa: $PORT"
else
  if [[ -n "${DEPLOY_IMAGE:-}" ]]; then
    PORT="${APP_PORT:-$(pick_port 3847)}"
    echo "==> Modo deploy: porta ${PORT} (defina APP_PORT=... para forçar)"
  else
    PORT="$(pick_port 3847)"
    echo "==> Porta escolhida (livre no servidor): $PORT"
  fi
fi

TMP_ENV="$(mktemp)"
cleanup() { rm -f "$TMP_ENV"; }
trap cleanup EXIT

{
  grep -Ev '^(APP_PORT|SQLITE_PATH|KTOR_DEPLOYMENT_PORT|DEPLOY_IMAGE|PUBLIC_HOST)=' "$LOCAL_ENV" || true
  echo "APP_PORT=$PORT"
  echo "SQLITE_PATH=/app/data/lead-engine.db"
  if [[ -n "${DEPLOY_IMAGE:-}" ]]; then
    echo "DEPLOY_IMAGE=$DEPLOY_IMAGE"
  fi
  if [[ -n "${PUBLIC_HOST:-}" ]]; then
    echo "PUBLIC_HOST=$PUBLIC_HOST"
  fi
} >"$TMP_ENV"

echo "==> Instalando Docker (se necessário) ..."
"${ssh_base[@]}" bash -s <<'REMOTE'
set -euo pipefail
if ! command -v docker >/dev/null 2>&1; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker.io docker-compose-v2 git
  systemctl enable --now docker
fi
command -v git >/dev/null 2>&1 || apt-get install -y -qq git
docker --version
docker compose version
REMOTE

if [[ -n "${DEPLOY_IMAGE:-}" ]]; then
  echo "==> Modo imagem pré-buildada (DEPLOY_IMAGE) — sem Gradle no servidor ..."
  "${ssh_base[@]}" "mkdir -p \"$REMOTE_DIR/data\""
  scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$DEPLOY_COMPOSE_SRC" "$HOST:$REMOTE_DIR/docker-compose.deploy.yml"
  if [[ -n "${PUBLIC_HOST:-}" ]]; then
    echo "==> HTTPS: enviando Caddy ($PUBLIC_HOST) ..."
    scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$CADDY_COMPOSE_SRC" "$HOST:$REMOTE_DIR/docker-compose.caddy.yml"
    scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$CADDYFILE_SRC" "$HOST:$REMOTE_DIR/Caddyfile"
  fi
  echo "==> Enviando .env (local + APP_PORT/SQLITE_PATH/DEPLOY_IMAGE) ..."
  scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$TMP_ENV" "$HOST:$REMOTE_DIR/.env"

  if [[ -n "${GHCR_PULL_TOKEN:-}" ]]; then
    gh_user="${GHCR_USER:-}"
    if [[ -z "$gh_user" ]]; then
      echo "GHCR_PULL_TOKEN definido: informe também GHCR_USER (usuário GitHub para docker login ghcr.io)" >&2
      exit 1
    fi
    echo "==> Login ghcr.io (pacote privado) ..."
    "${ssh_base[@]}" "docker logout ghcr.io 2>/dev/null || true"
    ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$HOST" "echo \"$GHCR_PULL_TOKEN\" | docker login ghcr.io -u \"$gh_user\" --password-stdin"
  fi

  echo "==> docker compose pull + up (sem build) ..."
  "${ssh_base[@]}" bash -s -- "$REMOTE_DIR" "$PORT" "${SKIP_PULL:-0}" "${PUBLIC_HOST:-}" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
PORT="$2"
SKIP_PULL_FLAG="${3:-0}"
PUBLIC_HOST_FLAG="${4:-}"
cd "$REMOTE_DIR"
COMPOSE_FILES=( -f docker-compose.deploy.yml )
COMPOSE_PROFILES=()
if [[ -n "$PUBLIC_HOST_FLAG" ]]; then
  COMPOSE_FILES+=( -f docker-compose.caddy.yml )
  COMPOSE_PROFILES=( --profile https )
  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q active; then
    ufw allow 80/tcp comment caddy-acme || true
    ufw allow 443/tcp comment caddy-https || true
  fi
fi
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q active; then
  ufw allow "$PORT/tcp" comment gear-lead-engine || true
fi
docker compose "${COMPOSE_FILES[@]}" "${COMPOSE_PROFILES[@]}" down --remove-orphans 2>/dev/null || true
if [[ "$SKIP_PULL_FLAG" != "1" ]]; then
  docker compose "${COMPOSE_FILES[@]}" "${COMPOSE_PROFILES[@]}" pull
else
  echo "(sem pull — imagem já no host, ex.: docker load)"
fi
docker compose "${COMPOSE_FILES[@]}" "${COMPOSE_PROFILES[@]}" up -d
sleep 12
curl -fsS "http://127.0.0.1:${PORT}/health"
REMOTE

else
  # --- Modo build no servidor (legado) ---
  if [[ "${SKIP_CLONE:-0}" == "1" ]]; then
    echo "==> SKIP_CLONE=1 — mantendo $REMOTE_DIR ..."
  else
    echo "==> Clonando repositório em $REMOTE_DIR (branch $GIT_BRANCH) ..."
    "${ssh_base[@]}" bash -s -- "$REMOTE_DIR" "$GIT_URL" "$GIT_BRANCH" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
GIT_URL="$2"
GIT_BRANCH="$3"
rm -rf "$REMOTE_DIR"
mkdir -p "$(dirname "$REMOTE_DIR")"
git clone --depth 1 --branch "$GIT_BRANCH" "$GIT_URL" "$REMOTE_DIR"
REMOTE
  fi

  echo "==> Enviando .env (local + APP_PORT/SQLITE_PATH) ..."
  scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$TMP_ENV" "$HOST:$REMOTE_DIR/.env"

  echo "==> Subindo stack (build em background — não trava o SSH no Gradle) ..."
  "${ssh_base[@]}" bash -s -- "$REMOTE_DIR" "$PORT" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
PORT="$2"
cd "$REMOTE_DIR"
mkdir -p data
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q active; then
  ufw allow "$PORT/tcp" comment gear-lead-engine || true
fi
docker compose down --remove-orphans 2>/dev/null || true
nohup docker compose up -d --build > /tmp/gear-lead-compose.log 2>&1 &
echo "compose_pid=$!"
REMOTE

  echo "==> Aguardando /health em 127.0.0.1:${PORT} (até ~30 min na 1ª build) ..."
  ok=0
  for attempt in $(seq 1 60); do
    if "${ssh_base[@]}" "curl -fsS --max-time 8 http://127.0.0.1:${PORT}/health" 2>/dev/null | grep -q ok; then
      echo "    OK (tentativa $attempt)"
      ok=1
      break
    fi
    echo "    ... tentativa $attempt/60 (30s)"
    sleep 30
  done
  if [[ "$ok" -ne 1 ]]; then
    echo "==> Timeout no health interno. Últimas linhas do log remoto:" >&2
    "${ssh_base[@]}" "tail -60 /tmp/gear-lead-compose.log" >&2 || true
    exit 1
  fi
fi

# --- Health (modo deploy: já validou no bloco remoto; reforço rápido) ---
if [[ -n "${DEPLOY_IMAGE:-}" ]]; then
  if ! "${ssh_base[@]}" "curl -fsS --max-time 10 http://127.0.0.1:${PORT}/health" 2>/dev/null | grep -q ok; then
    echo "==> Health falhou após deploy por imagem." >&2
    "${ssh_base[@]}" "cd \"$REMOTE_DIR\" && docker compose -f docker-compose.deploy.yml -f docker-compose.caddy.yml --profile https ps -a 2>/dev/null || docker compose -f docker-compose.deploy.yml ps -a; docker compose -f docker-compose.deploy.yml logs --tail=80 app" >&2 || true
    exit 1
  fi
  echo "==> Health interno OK (imagem pré-buildada)."
fi

DROPLET_IP="${HOST##*@}"
echo "==> Teste a partir da internet (porta ${PORT} liberada no firewall da DO) ..."
if curl -fsS --max-time 12 "http://${DROPLET_IP}:${PORT}/health" 2>/dev/null | grep -q ok; then
  echo "    Acesso externo OK: http://${DROPLET_IP}:${PORT}/health"
else
  echo "    AVISO: não consegui http://${DROPLET_IP}:${PORT}/health daqui." >&2
  echo "    Painel DO: firewall inbound TCP ${PORT}." >&2
fi

if [[ -n "${PUBLIC_HOST:-}" ]]; then
  echo "==> HTTPS (Caddy): aguardando certificado e testando https://${PUBLIC_HOST}/health ..."
  https_ok=0
  for attempt in $(seq 1 24); do
    if curl -fsS --max-time 15 "https://${PUBLIC_HOST}/health" 2>/dev/null | grep -q ok; then
      echo "    HTTPS OK (tentativa $attempt)"
      https_ok=1
      break
    fi
    echo "    ... HTTPS tentativa $attempt/24 (15s) — DNS/Let's Encrypt podem demorar"
    sleep 15
  done
  if [[ "$https_ok" -ne 1 ]]; then
    echo "    AVISO: HTTPS ainda não respondeu. Confira DNS A→${DROPLET_IP}, portas 80/443 e: ssh $HOST 'docker compose -f $REMOTE_DIR/docker-compose.caddy.yml logs caddy'" >&2
  fi
  echo "    Webhook Meta (callback): https://${PUBLIC_HOST}/webhooks/whatsapp"
  echo "    (alias) https://${PUBLIC_HOST}/whatsapp/webhook"
fi

echo "==> Verificação de IP público no servidor ..."
"${ssh_base[@]}" bash -s -- <<'REMOTE'
set -euo pipefail
PUB="$(curl -4 -sS --max-time 10 ifconfig.me || true)"
META="$(curl -4 -sS --max-time 10 https://api.ipify.org || true)"
echo "ifconfig.me (IPv4): ${PUB:-<vazio>}"
echo "api.ipify.org:       ${META:-<vazio>}"
PRIMARY="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}' || true)"
echo "IP de saída (route): ${PRIMARY:-<vazio>}"
REMOTE

echo
echo "Pronto."
echo "  http://${DROPLET_IP}:${PORT}/"
echo "  curl -fsS http://${DROPLET_IP}:${PORT}/health"
if [[ -z "${DEPLOY_IMAGE:-}" ]]; then
  echo "Log build remoto: ssh $HOST 'tail -f /tmp/gear-lead-compose.log'"
fi
