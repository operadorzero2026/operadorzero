# Arquitetura de producao

Em 2026-07-21 foi iniciada a transicao do prototipo para uma arquitetura real. A referencia executiva completa esta em `ARCHITECTURE.md`; os contratos complementares estao nos documentos da raiz.

## Estado

- Frontend React/Vite/TypeScript preservado, ainda dependente de dados locais em varios modulos.
- Fundacao Java 21 + Spring Boot 3.5 criada em `services/api`.
- PostgreSQL/Flyway definidos com migrations `V1` de identidade/RBAC/auditoria, `V2` de perfil/privacidade e `V3` de tokens/sessoes/OIDC.
- A `V1` usa indices unicos funcionais para e-mail e username normalizados, sintaxe compativel com PostgreSQL 17.
- Redis, health, readiness/liveness, Prometheus, logs JSON, OpenAPI e seguranca deny-by-default preparados.
- API adaptada ao `PORT` do Render, URL PostgreSQL, `REDIS_URL` e CORS com origens explicitas.
- A adaptacao de `DATABASE_URL` separa usuario e senha da URL JDBC, aplica a porta PostgreSQL padrao quando omitida e nunca registra o valor recebido.
- Build Docker sem usuario root, Vercel SPA, Render Blueprint gratuito de staging e GitHub Actions preparados.
- Frontend de staging publicado no dominio oficial `https://operadorzero.com.br`, com certificado HTTPS gerenciado pela Vercel e redirecionamento permanente de `www` para o dominio raiz.
- A API Render usa `FRONTEND_BASE_URL=https://operadorzero.com.br` e CORS com allowlist explicita do dominio oficial, `www` e endereco legado da Vercel durante a transicao.
- Bundle web validado para impedir credenciais e identificadores de segredo; demonstracao restrita ao desenvolvimento local.
- Identidade por e-mail/senha e Google OIDC implementada com sessao opaca `HttpOnly`, CSRF, rate limit Redis e recuperacao por e-mail.
- Readiness do Render valida estado da aplicacao, PostgreSQL e Redis sem acoplar reinicio da API a indisponibilidade temporaria da Resend.
- Confirmacao e recuperacao usam a API HTTPS da Resend, evitando as portas SMTP bloqueadas no Render Free; a chave nunca entra no frontend e cada envio usa chave de idempotencia.
- O dominio `mail.operadorzero.com.br` esta verificado no Resend e os e-mails de confirmacao e recuperacao foram entregues no E2E externo.
- Credenciais Google e chave Resend de staging foram salvas somente no Render. Google OIDC foi validado remotamente com CSRF, CORS, PKCE, `nonce`, callback, criacao de conta e sessao autenticada; o cliente externo esta `Em producao`, autoriza as origens do dominio oficial e mantem o callback exato na API Render.
- A sessao temporaria do Google OIDC usa Redis com namespace isolado e TTL de 10 minutos, evitando perda de `state`, `nonce` e PKCE quando o Render Free reinicia a API. A sessao opaca do usuario permanece separada no PostgreSQL.
- Staging web identificado visualmente e bloqueado para indexacao; autenticacao para usuarios reais so deve ser liberada depois de homologar remetente Resend, callback Google, termos, privacidade e os demais gates desta pagina.

## Limite honesto

O Operador Zero ainda nao esta pronto para operacao completa com usuarios reais. Google OIDC e e-mails transacionais passaram no E2E de staging, mas o segredo Google anterior, DMARC e bounces ainda exigem conclusao. O PostgreSQL gratuito sera excluido em 20 de agosto de 2026 se nao houver upgrade; a API gratuita hiberna e o Redis gratuito nao possui persistencia. Autorizacao de casos de uso, S3 seguro, moderacao, migracao dos dados simulados, backup restaurado, termos/privacidade e observabilidade externa continuam pendentes. A plataforma nao processa movimentacao financeira. Recursos gratuitos do Render sao apenas staging e a escolha de plano comercial da Vercel/Render exige autorizacao explicita.

## Sequencia

Fundacao -> identidade/RBAC -> operador/equipe/operacoes -> ranking -> classificados/comunidade/chat -> arquivos -> destaques gratuitos -> endurecimento operacional. Ver [[07-ROADMAP-MVP]], [[05-SEGURANCA-E-PRIVACIDADE]] e [[99-HISTORICO-DE-ALTERACOES]].

## Baseline de autenticacao endurecida em 2026-07-27

A identidade exige `AUTH_HASH_KEY` exclusiva do backend para HMAC-SHA-256, preparacao de uso unico antes do Google OIDC, rate limit por IP e sujeito associado ao IP e limpeza indexada de intencoes OAuth. O Redis de staging usa `noeviction`. Antes de qualquer deploy, a chave deve ser cadastrada no Render; sua primeira ativacao invalida sessoes e links antigos. A migration `V5` permanece aditiva e nenhuma alteracao remota foi executada nesta etapa.
