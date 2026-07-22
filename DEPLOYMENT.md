# Implantacao

Ambientes isolados: local, test, staging e production, cada um com banco, Redis, objetos, chaves e contas externas separados. Build gera artefatos imutaveis para SPA e API. O proxy termina TLS, aplica headers, limites e encaminha `/api`.

Pipeline esperado: lint -> testes frontend/backend -> analise de dependencias/segredos -> build -> migrations verificadas -> deploy staging -> smoke/integracao -> aprovacao -> backup -> deploy gradual production -> smoke -> monitoramento/rollback. Nunca executar Flyway clean em producao.

Configuracao versionada: `.github/workflows/ci.yml`, `vercel.json`, `render.yaml` e `services/api/Dockerfile`. Procedimentos: `VERCEL-DEPLOYMENT.md`, `RENDER-DEPLOYMENT.md` e `ROLLBACK.md`.

O Blueprint atual e exclusivamente de staging gratuito. Ele nao representa uma arquitetura de producao aprovada e nao autoriza custos.

## Identidade no staging

Antes de definir `AUTH_ENABLED=true`, configure no Render `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `RESEND_API_KEY`, `MAIL_FROM` e os cookies seguros. No Vercel, configure somente valores publicos: `VITE_API_URL`, `VITE_AUTH_ENABLED=true` e `VITE_APP_ENV=staging`. Google e opcional e exige `GOOGLE_AUTH_ENABLED=true`, client ID/secret somente no Render e redirect URI exata cadastrada no provedor.

O link nao deve ser divulgado para usuarios reais enquanto e-mail, backup, monitoramento, termos/privacidade e testes E2E externos permanecerem pendentes. Os modulos depois do login ainda mostram dados demonstrativos e nao persistem operacoes de negocio.

O health check do Render usa `/actuator/health/readiness`, composto por estado de prontidao, PostgreSQL e Redis. A configuracao da Resend possui health separado e acompanha `MAIL_ENABLED`; indisponibilidade temporaria de e-mail deve gerar alerta e retentativa operacional, nao reinicio automatico da API.
