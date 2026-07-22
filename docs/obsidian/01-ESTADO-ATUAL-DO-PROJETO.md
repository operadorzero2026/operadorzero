# Estado atual do projeto

## 2026-07-22

- Cadastro por e-mail e senha, confirmacao de e-mail, login, recuperacao de senha, sessao e logout foram conectados entre SPA e API.
- Senhas usam Argon2id; tokens de verificacao, recuperacao e sessao sao opacos e persistidos somente como hash.
- A sessao real usa cookie `HttpOnly`; mutacoes exigem CSRF; cadastro/login/recuperacao usam rate limit no Redis.
- Google OIDC com Authorization Code, PKCE, `state`, `nonce` e validacao do provedor esta implementado, mas depende das credenciais externas do ambiente.
- A migration `V3__functional_identity.sql` adiciona tokens, sessoes, identidades OIDC e papeis iniciais.
- O frontend restaura a sessao da API e nao usa `localStorage` ou `sessionStorage` para credenciais.
- Modulos de negocio exibidos apos o login continuam demonstrativos e nao persistem dados. O marco atual permite homologar identidade, nao receber usuarios reais em todos os modulos.
- Antes de usuarios reais ainda faltam SMTP/DNS de e-mail, termos e privacidade aprovados, backup restaurado, monitoramento e os gates de [[16-ARQUITETURA-DE-PRODUCAO]].

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
