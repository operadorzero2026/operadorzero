# Arquitetura de producao

Em 2026-07-21 foi iniciada a transicao do prototipo para uma arquitetura real. A referencia executiva completa esta em `ARCHITECTURE.md`; os contratos complementares estao nos documentos da raiz.

## Estado

- Frontend React/Vite/TypeScript preservado, ainda dependente de dados locais em varios modulos.
- Fundacao Java 21 + Spring Boot 3.5 criada em `services/api`.
- PostgreSQL/Flyway definidos com migrations `V1` de identidade/RBAC/auditoria e `V2` de perfil/privacidade.
- Redis, health, readiness/liveness, Prometheus, logs JSON, OpenAPI e seguranca deny-by-default preparados.

## Limite honesto

O Operador Zero ainda nao esta pronto para producao. Login real, autorizacao de casos de uso, S3 seguro, e-mail, moderacao, Mercado Pago, migracao dos dados simulados, CI/CD, backup restaurado e observabilidade externa continuam pendentes.

## Sequencia

Fundacao -> identidade/RBAC -> operador/equipe/operacoes -> ranking -> classificados/comunidade/chat -> arquivos -> financeiro/pagamentos -> endurecimento operacional. Ver [[07-ROADMAP-MVP]], [[05-SEGURANCA-E-PRIVACIDADE]] e [[99-HISTORICO-DE-ALTERACOES]].

