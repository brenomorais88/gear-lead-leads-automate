# Deploy Ubuntu com Docker Compose

Este guia sobe o Gear Lead Engine com o mínimo de passos manuais.

## 1) Pré-requisitos no servidor

- Docker Engine + plugin Docker Compose instalados.
- Porta `3000` liberada no firewall (se precisar acesso direto).

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

## 3) Subir stack

Somente backend:

```bash
docker compose up -d --build
```

Backend + tunnel Cloudflare temporário (trycloudflare):

```bash
docker compose --profile tunnel up -d --build
```

## 4) Comandos operacionais

- Subir/atualizar: `docker compose up -d --build`
- Logs: `docker compose logs -f`
- Logs de um serviço: `docker compose logs -f app` / `docker compose logs -f cloudflared`
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

## 7) URL pública com Cloudflared

Quando subir com profile `tunnel`, pegue a URL via logs:

```bash
docker compose logs -f cloudflared
```

Procure a linha com domínio `https://...trycloudflare.com`.

## 8) Configurar webhook da Meta

Com a URL pública do tunnel:

1. Callback URL: `https://SEU-TUNNEL.trycloudflare.com/whatsapp/webhook`
2. Verify token: valor de `WHATSAPP_WEBHOOK_VERIFY_TOKEN` do `.env`
3. Salve e envie evento de teste no painel da Meta.

## 9) Teste rápido pós-deploy

1. `docker compose ps` (serviços `up`/`healthy`)
2. `curl http://localhost:3000/health` retorna `ok`
3. Abrir painel em `http://localhost:3000`
4. Se tunnel ativo, validar `GET /health` também pela URL pública
