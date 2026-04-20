# Deploy Ubuntu com Docker Compose

Este guia sobe o Gear Lead Engine com o mínimo de passos manuais.

## 1) Pré-requisitos no servidor

- Docker Engine + plugin Docker Compose instalados.
- Porta do app no host (`APP_PORT`, ex. `3847`) liberada se for acesso direto HTTP.
- Para HTTPS com Caddy: **TCP 80** e **TCP 443** liberados; domínio com **DNS A** (ou AAAA) para o IP da VPS.

## 2) Clonar projeto e configurar ambiente

```bash
git clone <URL_DO_REPOSITORIO>
cd gear-lead-engine
cp .env.example .env
```

Edite o `.env` e preencha pelo menos:

- `WHATSAPP_ACCESS_TOKEN`
- `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
- `WHATSAPP_BUSINESS_ACCOUNT_ID` (se aplicável no seu fluxo)

Opcional (notificação para operador quando lead responde):

- `WHATSAPP_INBOUND_NOTIFY_RECIPIENTS` (um ou mais números no formato internacional; separador `,` ou `;`)
- `WHATSAPP_INBOUND_NOTIFY_TEMPLATE_NAME` (template para aviso interno)
- `WHATSAPP_INBOUND_NOTIFY_TEMPLATE_LANGUAGE` (ex.: `pt_BR`)
- `WHATSAPP_INBOUND_NOTIFY_BODY_TEMPLATE` (1 parâmetro do corpo, com placeholders `{{lead_name}}`, `{{lead_phone}}`, `{{message}}`)

## 3) Subir stack

### 3a) Local / servidor com build (Gradle no Docker — exige CPU/RAM)

Somente backend:

```bash
docker compose up -d --build
```

### 3b) Servidor fraco: só **pull** da imagem (build na esteira GitHub Actions → GHCR)

No GitHub, ao dar push em `main` ou `integracao_whats`, o workflow **Build and push Docker image** publica em:

`ghcr.io/<dono>/<repositório>:<nome-da-branch>`

Exemplo (ajuste dono/repo ao seu GitHub):

`ghcr.io/brenomorais88/gear-lead-leads-automate:integracao_whats`

No servidor (pasta com `docker-compose.deploy.yml`, `.env` e diretório `./data`):

1. No `.env`, defina **`DEPLOY_IMAGE`** com a tag acima (e `APP_PORT`, `SQLITE_PATH`, segredos WhatsApp como de costume).
2. Se o pacote GHCR for **privado**, faça login uma vez: `echo SEU_PAT | docker login ghcr.io -u SEU_USUARIO_GITHUB --password-stdin` (PAT com escopo `read:packages`).
3. Subir **sem** build:

```bash
docker compose -f docker-compose.deploy.yml pull
docker compose -f docker-compose.deploy.yml up -d
```

Atualizar versão: altere a tag em `DEPLOY_IMAGE` (ou use `:sha-<commit>` gerada pela esteira) e repita `pull` + `up -d`.

A esteira gera imagem **`linux/amd64`** (droplets DigitalOcean comuns). Build de imagem no Mac Apple Silicon **sem** `--platform` não serve para subir com `docker load` nesses servidores — use a imagem do GHCR ou `docker buildx build --platform linux/amd64`.

Script local (Mac) que envia `.env` + compose e só faz pull no droplet:

```bash
DEPLOY_IMAGE=ghcr.io/brenomorais88/gear-lead-leads-automate:integracao_whats \
  SKIP_CLONE=1 bash scripts/bootstrap-do-droplet.sh root@SEU_IP
```

(`SKIP_CLONE=1` evita apagar `./data` se o diretório já existir com clone antigo; com `DEPLOY_IMAGE` o script usa `docker-compose.deploy.yml` e **não** roda `docker compose build` no servidor.)

### 3c) HTTPS no próprio servidor (Caddy + Let’s Encrypt)

1. Crie um **subdomínio** (ex.: `webhook.brenomorais.com.br`) na Hostinger (ou outro DNS) com registro **A** apontando para o **IP público** da VPS.
2. Abra **TCP 80** e **TCP 443** no firewall da VPS (e na DigitalOcean Cloud Firewall, se usar).
3. No `.env` do servidor: `PUBLIC_HOST=webhook.brenomorais.com.br` (sem `https://`).
4. Na pasta do deploy (`docker-compose.deploy.yml`, `docker-compose.caddy.yml`, `Caddyfile`, `.env`, `./data`):

```bash
docker compose -f docker-compose.deploy.yml -f docker-compose.caddy.yml --profile https pull
docker compose -f docker-compose.deploy.yml -f docker-compose.caddy.yml --profile https up -d
```

O Caddy obtém certificado automaticamente na primeira requisição válida (HTTP-01 na porta 80).

Com o script local (também envia Caddy + espera HTTPS):

```bash
PUBLIC_HOST=webhook.brenomorais.com.br \
DEPLOY_IMAGE=ghcr.io/brenomorais88/gear-lead-leads-automate:integracao_whats \
SKIP_CLONE=1 bash scripts/bootstrap-do-droplet.sh root@SEU_IP
```

**Produção com webhook Meta:** use DNS na Hostinger (ou outro provedor) com registro **A** do subdomínio apontando para o **IP público** da VPS e HTTPS no servidor com **Caddy** conforme **3c** acima. O `docker-compose.yml` deste repositório contém apenas o serviço `app`.

Em casa, IP de residência costuma ser **dinâmico**; para URL estável costuma ser melhor hospedar na VPS com IP fixo.

## 4) Comandos operacionais

- Subir/atualizar: `docker compose up -d --build`
- Logs: `docker compose logs -f`
- Logs do app: `docker compose logs -f app`
- Reiniciar: `docker compose restart`
- Parar mantendo dados: `docker compose down`

## 5) Saúde da aplicação

- Healthcheck interno do compose usa `GET /health`.
- Teste manual:

```bash
curl http://localhost:3000/health
```

Resposta esperada: `ok`

## 6) Persistência SQLite

- Banco configurado por `SQLITE_PATH` (default: `/app/data/lead-engine.db`).
- No compose, `./data` do host é montado em `/app/data`.
- Reiniciar/atualizar containers **não** apaga o banco.

## 7) URL pública (DNS + HTTPS no servidor)

1. Na Hostinger, crie o registro **A** do subdomínio (ex.: `webhook`) para o **IP público** da VPS (propagação pode levar alguns minutos).
2. Com **Caddy** ativo (**3c**), o app fica acessível em `https://SEU_PUBLIC_HOST/`.
3. Valide: `curl -fsS https://SEU_PUBLIC_HOST/health` deve retornar `ok`.

## 8) Configurar webhook da Meta

O app expõe **as duas** URLs abaixo (mesmo comportamento). Use **HTTPS** no painel da Meta.

| Uso | URL de callback |
|-----|------------------|
| **Recomendada** | `https://SEU_DOMINIO/webhooks/whatsapp` |
| Compatível (alias) | `https://SEU_DOMINIO/whatsapp/webhook` |

1. **Callback URL** (campo no app WhatsApp / Cloud API): uma das linhas acima com o seu domínio HTTPS (ex.: `https://webhook.seudominio.com.br/webhooks/whatsapp`).
2. **Verify token:** exatamente o valor de `WHATSAPP_WEBHOOK_VERIFY_TOKEN` do `.env` (o mesmo usado na verificação GET).
3. Salve no painel da Meta e use **“Verify and save”** (envia `GET` com `hub.mode`, `hub.verify_token`, `hub.challenge`).

**Teste manual da verificação (substitua TOKEN e HOST):**

```bash
curl -sS "https://HOST/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=TOKEN&hub.challenge=pong"
```

Resposta esperada: corpo texto `pong` (HTTP 200). O mesmo comando funciona trocando o path por `/whatsapp/webhook`.

**POST de eventos:** a Meta envia para o mesmo path do callback (ex.: `POST https://HOST/webhooks/whatsapp`). O app responde `200` após processar (erros de parse são logados e ainda retornam 200 para evitar retries infinitos em payloads inválidos).

## 9) Teste rápido pós-deploy

1. `docker compose ps` (serviços `up`/`healthy`)
2. `curl http://localhost:3000/health` retorna `ok`
3. Abrir painel em `http://localhost:3000`
4. Validar `GET /health` pela URL HTTPS pública (ex.: `curl -fsS https://SEU_PUBLIC_HOST/health`)

## 10) Auto-deploy via GitHub Actions (main)

O workflow `.github/workflows/docker-publish.yml` faz:

1. Build + push da imagem em `ghcr.io/<owner>/<repo>:sha-<commit>`
2. Deploy automático no servidor (apenas em `main`)
3. Verificações obrigatórias no final:
   - imagem aplicada no container `gear-lead-engine-app` é exatamente a tag `sha-<commit>`
   - `GET http://127.0.0.1:<APP_PORT>/health` retorna sucesso
   - se `PUBLIC_HOST` estiver configurado: `GET https://<PUBLIC_HOST>/health` também retorna sucesso

Secrets necessários no GitHub (Settings → Secrets and variables → Actions):

- `DEPLOY_HOST`: IP/DNS do servidor
- `DEPLOY_USER`: usuário SSH (ex.: `root`)
- `DEPLOY_SSH_KEY`: chave privada SSH
- `DEPLOY_PATH`: pasta do deploy no servidor (ex.: `/opt/gear-lead-leads-automate`)
- `GHCR_USER`: usuário GitHub com permissão de pull no pacote GHCR
- `GHCR_TOKEN`: token com `read:packages`
- `APP_PORT` (opcional): porta pública, se diferente da `.env` do servidor
- `PUBLIC_HOST` (opcional): domínio HTTPS para validar health externo
