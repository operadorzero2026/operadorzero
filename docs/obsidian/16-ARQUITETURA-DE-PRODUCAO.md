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
- Bundle web validado para impedir credenciais e identificadores de segredo; demonstracao restrita ao desenvolvimento local.
- Identidade por e-mail/senha e Google OIDC implementada com sessao opaca `HttpOnly`, CSRF, rate limit Redis e recuperacao por e-mail.
- Readiness do Render valida estado da aplicacao, PostgreSQL e Redis sem acoplar reinicio da API a indisponibilidade temporaria da Resend.
- Confirmacao e recuperacao usam a API HTTPS da Resend, evitando as portas SMTP bloqueadas no Render Free; a chave nunca entra no frontend e cada envio usa chave de idempotencia.
- Credenciais Google e chave Resend de staging foram salvas somente no Render. O inicio do Google OIDC foi validado remotamente ate o redirecionamento oficial, com CSRF, CORS, PKCE e `nonce`; callback e sessao autenticada ainda aguardam E2E.
- Staging web identificado visualmente e bloqueado para indexacao; autenticacao para usuarios reais so deve ser liberada depois de homologar remetente Resend, callback Google, termos, privacidade e os demais gates desta pagina.

## Limite honesto

O Operador Zero ainda nao esta pronto para operacao completa com usuarios reais. As credenciais de staging existem, mas Resend ainda precisa de DNS e entrega E2E, e Google precisa do callback/sessao E2E e rotacao do segredo anterior. Autorizacao de casos de uso, S3 seguro, moderacao, Mercado Pago, migracao dos dados simulados, backup restaurado, termos/privacidade e observabilidade externa continuam pendentes. Recursos gratuitos do Render sao apenas staging e a escolha de plano comercial da Vercel/Render exige autorizacao explicita.

## Sequencia

Fundacao -> identidade/RBAC -> operador/equipe/operacoes -> ranking -> classificados/comunidade/chat -> arquivos -> financeiro/pagamentos -> endurecimento operacional. Ver [[07-ROADMAP-MVP]], [[05-SEGURANCA-E-PRIVACIDADE]] e [[99-HISTORICO-DE-ALTERACOES]].
