# Arquitetura de producao

Em 2026-07-21 foi iniciada a transicao do prototipo para uma arquitetura real. A referencia executiva completa esta em `ARCHITECTURE.md`; os contratos complementares estao nos documentos da raiz.

## Estado

- Frontend React/Vite/TypeScript preservado, ainda dependente de dados locais em varios modulos.
- Fundacao Java 21 + Spring Boot 3.5 criada em `services/api`.
- PostgreSQL/Flyway definidos com migrations `V1` de identidade/RBAC/auditoria e `V2` de perfil/privacidade.
- Redis, health, readiness/liveness, Prometheus, logs JSON, OpenAPI e seguranca deny-by-default preparados.
- API adaptada ao `PORT` do Render, URL PostgreSQL, `REDIS_URL` e CORS com origens explicitas.
- A adaptacao de `DATABASE_URL` separa usuario e senha da URL JDBC, aplica a porta PostgreSQL padrao quando omitida e nunca registra o valor recebido.
- Build Docker sem usuario root, Vercel SPA, Render Blueprint gratuito de staging e GitHub Actions preparados.
- Bundle web validado para impedir credenciais e identificadores de segredo; demonstracao restrita ao desenvolvimento local.
- Staging web identificado visualmente, bloqueado para indexacao e com autenticacao remota desabilitada ate a implementacao dos endpoints reais.

## Limite honesto

O Operador Zero ainda nao esta pronto para producao. Login real, autorizacao de casos de uso, S3 seguro, e-mail, moderacao, Mercado Pago, migracao dos dados simulados, backup restaurado e observabilidade externa continuam pendentes. Recursos gratuitos do Render sao apenas staging e a escolha de plano comercial da Vercel/Render exige autorizacao explicita.

## Sequencia

Fundacao -> identidade/RBAC -> operador/equipe/operacoes -> ranking -> classificados/comunidade/chat -> arquivos -> financeiro/pagamentos -> endurecimento operacional. Ver [[07-ROADMAP-MVP]], [[05-SEGURANCA-E-PRIVACIDADE]] e [[99-HISTORICO-DE-ALTERACOES]].
