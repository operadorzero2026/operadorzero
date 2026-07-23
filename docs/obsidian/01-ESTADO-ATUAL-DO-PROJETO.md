# Estado atual do projeto

## 2026-07-23

- As credenciais Google OIDC de staging foram configuradas somente no backend do Render e `GOOGLE_AUTH_ENABLED` foi ativado.
- O fluxo remoto foi validado de forma nao destrutiva: readiness `200`, emissao de intent com CSRF, redirecionamento `302` para `accounts.google.com` e CORS com credenciais limitado a `https://operadorzero.vercel.app`.
- Um teste de regressao garante que `/oauth2/authorization/google` mantenha OIDC, PKCE e `nonce` quando o provedor estiver habilitado.
- Ainda falta concluir o callback com uma conta Google de teste, validar a sessao resultante e, depois disso, desativar o segredo Google anterior.
- O primeiro callback real revelou que o Render Free pode reiniciar a API entre a autorizacao e o retorno. O estado temporario do OAuth foi movido da memoria da instancia para Redis, com expiracao de 10 minutos e namespace isolado; o E2E remoto precisa ser repetido apos o deploy.
- Falhas Google agora registram apenas classificacao tecnica sanitizada, permitindo distinguir estado perdido de falha na troca do token sem expor dados do provedor.
- A chave Resend foi salva no Render, mas DNS do remetente, entrega real, confirmacao e recuperacao E2E continuam pendentes.
- Os modulos de negocio apos o login continuam demonstrativos; o sistema ainda nao esta liberado para usuarios reais. Ver [[16-ARQUITETURA-DE-PRODUCAO]].

## 2026-07-22

- Cadastro por e-mail e senha, confirmacao de e-mail, login, recuperacao de senha, sessao e logout foram conectados entre SPA e API.
- Senhas usam Argon2id; tokens de verificacao, recuperacao e sessao sao opacos e persistidos somente como hash.
- A sessao real usa cookie `HttpOnly`; mutacoes exigem CSRF; cadastro/login/recuperacao usam rate limit no Redis.
- Google OIDC com Authorization Code, PKCE, `state`, `nonce` e validacao do provedor esta implementado, mas depende das credenciais externas do ambiente.
- A migration `V3__functional_identity.sql` adiciona tokens, sessoes, identidades OIDC e papeis iniciais.
- O frontend restaura a sessao da API e nao usa `localStorage` ou `sessionStorage` para credenciais.
- O envio de confirmacao e recuperacao foi migrado do SMTP para a API HTTPS da Resend, com chave exclusiva do backend e idempotencia. A chave, o dominio e o E2E externo ainda precisam ser configurados.
- Modulos de negocio exibidos apos o login continuam demonstrativos e nao persistem dados. O marco atual permite homologar identidade, nao receber usuarios reais em todos os modulos.
- Antes de usuarios reais ainda faltam DNS/homologacao da Resend, termos e privacidade aprovados, backup restaurado, monitoramento e os gates de [[16-ARQUITETURA-DE-PRODUCAO]].

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
