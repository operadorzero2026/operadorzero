# Operador Zero

Plataforma brasileira para operadores, equipes, operacoes, ranking, classificados e comunidade de airsoft. O repositorio esta em transicao de prototipo visual para uma aplicacao com backend real.

## Estado atual

- Frontend: React, TypeScript e Vite na raiz; varios modulos ainda usam dados ficticios em memoria.
- API: Java 21, Spring Boot, Spring Security, JPA, Flyway, PostgreSQL e Redis em `services/api`.
- Seguranca: API deny-by-default, CORS explicito, health check publico, erros sem stack trace e nenhum segredo permitido no bundle web.
- Deploy preparado: Vercel para a SPA e Render Blueprint para API, PostgreSQL e Key Value de staging.
- Plataforma gratuita: nao processa cobrancas, nao recebe valores, nao calcula comissao e nao possui publicidade paga.
- Bloqueio de producao: modulos persistentes, autorizacao por objeto, uploads, moderacao e backup restaurado ainda precisam ser concluidos.

## Desenvolvimento

Consulte [LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md). Para publicar, siga [VERCEL-DEPLOYMENT.md](VERCEL-DEPLOYMENT.md), [RENDER-DEPLOYMENT.md](RENDER-DEPLOYMENT.md), [PRODUCTION-CHECKLIST.md](PRODUCTION-CHECKLIST.md) e [ROLLBACK.md](ROLLBACK.md).

## Comandos de verificacao

```bash
npm ci
npm run check
cd services/api
mvn clean verify
```

Nunca adicione senhas, tokens, chaves, URLs assinadas ou credenciais de banco a arquivos `VITE_*`, ao Git ou ao armazenamento do navegador.
