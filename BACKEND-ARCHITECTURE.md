# Arquitetura do backend

API principal: Java 21 + Spring Boot 3.5, em `services/api`. O desenho e monolito modular: controller apenas traduz HTTP; application/service orquestra casos de uso; dominio concentra regras; repositories/adapters isolam PostgreSQL, Redis, S3 e e-mail.

Regras transversais: identificador do usuario vem do token validado; autorizacao e checada por acao e objeto; entrada usa DTO + Bean Validation; consultas parametrizadas; migrations Flyway; erros nao expõem stack trace; toda mutacao sensivel gera auditoria; idempotency key em comandos externos; paginacao e limites em colecoes.

O backend continua `deny-by-default`. Alem de health/OpenAPI, somente os endpoints de identidade explicitamente listados em `SecurityConfig` estao expostos. Cadastro, verificacao, login, sessao, logout e recuperacao por e-mail vivem no modulo `identity`; as demais rotas continuam negadas ate receberem autorizacao por caso de uso e objeto.

A migration histórica `V3__functional_identity.sql` criou tokens e sessões; a `V17__retire_social_authentication.sql` preserva usuários, revoga sessões antigas e remove as tabelas exclusivas do mecanismo aposentado. `IdentityRepository` usa SQL parametrizado; `AuthRateLimiter` usa Redis; `AuthMailListener` envia links somente depois do commit. Consulte `AUTHENTICATION.md`, `AUTHORIZATION.md` e `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`.
