# Operador Zero em VPS própria

Roda o site, a API, o PostgreSQL e o Redis em Docker num VPS que já hospeda outra aplicação, sem mexer nela.

```text
navegador ── HTTPS ──> proxy do servidor (já existente) ──> 127.0.0.1:8081
                                                              │
                                                    web (Nginx interno: SPA + /api)
                                                              │
                                                     api (Spring Boot) ── postgres, redis
```

- Só o contêiner `web` publica porta, e apenas em `127.0.0.1` (padrão `8081`). Postgres e Redis ficam numa rede interna sem acesso externo.
- O HTTPS continua no proxy que já atende a outra aplicação; ele ganha só um bloco novo para `operadorzero.com.br`.
- Limites de memória: API 1 GB, Postgres 512 MB, Redis 256 MB, web 128 MB.

## 1. Preparar o servidor

Docker e o plugin Compose precisam estar instalados (`docker compose version`). Se a porta `8081` já estiver em uso (`sudo ss -tlnp | grep 8081`), escolha outra em `OZ_HTTP_PORT`.

```bash
sudo git clone https://github.com/operadorzero2026/operadorzero.git /opt/operadorzero
cd /opt/operadorzero
sudo cp deploy/vps/.env.example deploy/vps/.env
sudo chmod 600 deploy/vps/.env
```

Preencha `deploy/vps/.env`:

- `DATABASE_PASSWORD`, `REDIS_PASSWORD` e `AUTH_HASH_KEY`: gere cada um com `openssl rand -base64 48`. Não troque `AUTH_HASH_KEY` depois, pois isso derruba todas as sessões.
- `MAIL_FROM` e `RESEND_API_KEY`: os mesmos valores configurados hoje no Render (painel do serviço → Environment).

## 2. Subir

```bash
cd /opt/operadorzero
sudo docker compose -f deploy/vps/compose.yml up -d --build
sudo docker compose -f deploy/vps/compose.yml ps
curl -s http://127.0.0.1:8081/actuator/health/readiness
```

O primeiro build leva alguns minutos (Maven e npm). A resposta esperada do último comando é `{"status":"UP"}`. O banco começa vazio e as migrations do Flyway rodam sozinhas.

## 3. Ligar o domínio no proxy do servidor

Use o bloco do proxy que já atende a outra aplicação. Os dois exemplos repassam o IP real em `X-Real-IP`, que o rate limit de login usa.

**Nginx** — crie `/etc/nginx/sites-available/operadorzero` com o conteúdo de [`host-nginx.conf`](host-nginx.conf), depois:

```bash
sudo ln -s /etc/nginx/sites-available/operadorzero /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d operadorzero.com.br -d www.operadorzero.com.br
```

**Caddy** — acrescente ao `Caddyfile` o conteúdo de [`host-Caddyfile`](host-Caddyfile) e rode `sudo systemctl reload caddy`. O certificado é emitido automaticamente.

Se o proxy atual também roda em Docker (Traefik, nginx-proxy, Caddy em contêiner), `127.0.0.1` dentro dele não é o servidor: ligue o contêiner `web` à rede do proxy e aponte para `operadorzero-web-1:8080`.

## 4. Trocar o DNS

No Registro.br, aponte `operadorzero.com.br` (registro A) e `www` para o IP do VPS e remova os registros da Vercel. Antes da troca, teste sem mexer no DNS:

```bash
curl -sI --resolve operadorzero.com.br:443:IP_DO_VPS https://operadorzero.com.br/
```

O certificado só é emitido depois que o DNS apontar para o VPS; até lá o teste acima falha no TLS, e dá para testar por HTTP no próprio servidor com `curl -s http://127.0.0.1:8081/`.

## 5. Backup

```bash
sudo chmod +x /opt/operadorzero/deploy/vps/backup.sh
sudo crontab -e
# 30 3 * * * OFFSITE_REMOTE=gdrive-crypt: /opt/operadorzero/deploy/vps/backup.sh >> /var/log/operadorzero-backup.log 2>&1
```

Guarda 14 dumps em `/var/backups/operadorzero`. Com `OFFSITE_REMOTE`, envia cada dump para fora do VPS e mantém lá 60 dias (`OFFSITE_KEEP_DAYS`).

Cópia externa no Google Drive, criptografada com rclone:

```bash
sudo apt-get install -y rclone
# Autorização: abra um túnel do seu computador (ssh -L 53682:127.0.0.1:53682 root@SERVIDOR)
# e, nessa sessão, rode o comando abaixo; abra no navegador o link http://127.0.0.1:53682/auth?... que ele mostrar.
rclone config create gdrive drive scope=drive.file
rclone mkdir gdrive:operadorzero-backups
# Senha de criptografia: digite-a sem colar e guarde-a fora do servidor. Sem ela os backups não abrem.
rclone config create gdrive-crypt crypt remote=gdrive:operadorzero-backups filename_encryption=off directory_name_encryption=false password=SENHA --obscure
```

Restaurar:

```bash
sudo docker compose -f deploy/vps/compose.yml exec -T postgres pg_restore -U operadorzero -d operadorzero --clean --if-exists < ARQUIVO.dump
# Se o servidor foi perdido: reinstale o rclone, refaça os dois "config create" com a MESMA senha e baixe o dump:
rclone copy gdrive-crypt:operadorzero-AAAAMMDD-HHMMSS.dump .
```

## 6. Login com Google

1. No Google Cloud (Google Auth Platform), crie um cliente OAuth do tipo **Aplicativo da Web** com:
   - Origem JavaScript autorizada: `https://operadorzero.com.br`
   - URI de redirecionamento autorizado: `https://operadorzero.com.br/login/oauth2/code/google`
2. Grave no `.env` (sem colar em chat): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` e `GOOGLE_AUTH_ENABLED=true`.
3. Recrie a API: `sudo docker compose -f deploy/vps/compose.yml --env-file deploy/vps/.env up -d api`.

O Nginx interno encaminha somente `/oauth2/authorization/google` e `/login/oauth2/code/google` para a API.

## Atualizar

```bash
cd /opt/operadorzero
sudo git pull
sudo docker compose -f deploy/vps/compose.yml up -d --build
```

## Voltar para Vercel e Render

Vercel e Render continuam configurados. Para voltar, aponte o DNS de novo para a Vercel. Dados criados no VPS não vão para o banco do Render.

## Comandos úteis

```bash
sudo docker compose -f deploy/vps/compose.yml logs -f api
sudo docker compose -f deploy/vps/compose.yml restart api
sudo docker stats --no-stream
```
