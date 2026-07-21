# Estado atual do projeto

## 2026-07-21

- Fundacao compilavel do backend Java 21/Spring Boot 3.5 criada.
- PostgreSQL/Flyway, Redis, health/metricas, logs estruturados, OpenAPI e deny-by-default preparados.
- Migrations iniciais cobrem identidade, RBAC, auditoria, perfil e privacidade.
- O frontend continua demonstrativo e ainda nao consome casos de uso reais.
- **O produto nao esta pronto para producao.** Ver [[16-ARQUITETURA-DE-PRODUCAO]].

## 2026-07-20

Fase zero iniciada: arquitetura, regras, segurança e primeira experiência pública.

- Implementado: base React + TypeScript + Vite, landing responsiva, experiência visual de autenticação, dashboard demonstrativo e primeira fatia frontend dos Classificados Operador Zero.
- Documentado: módulos, entidades, fluxos, ranking, privacidade e políticas de segurança.
- Não implementado: API, banco, autenticação efetiva, Google OAuth/OIDC, envio de e-mail, uploads, moderação real, Classificados persistentes, operações reais e deploy.
- Dados da interface atual: somente demonstrativos, identificados visualmente como fictícios.

Próximo marco: fundação do backend modular, schema PostgreSQL, autenticação segura e primeira fatia vertical de operador + operação.

Veja [[07-ROADMAP-MVP]] e [[99-HISTORICO-DE-ALTERACOES]].
