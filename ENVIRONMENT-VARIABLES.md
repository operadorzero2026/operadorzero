# Variaveis de ambiente

Modelo local em `.env.example`. Na API de staging sao obrigatorias: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_URL`, `CORS_ALLOWED_ORIGINS` e `SPRING_PROFILES_ACTIVE`. Para identidade real tambem sao obrigatorias `RESEND_API_KEY`, `MAIL_FROM`, `MAIL_ENABLED=true`, `AUTH_ENABLED=true` e `FRONTEND_BASE_URL`. `PORT` e fornecida pelo Render. No frontend, somente variaveis publicas `VITE_*` podem existir.

Producao usa cofre de segredos, rotacao, identidade por workload quando disponivel e acesso minimo. Valores reais nao entram em `.env`, compose, logs, imagens ou repositorio. Variaveis publicas do Vite jamais guardam segredo.

`RESEND_API_KEY` e qualquer chave OIDC, S3 ou telemetria pertencem somente ao backend e ao cofre do provedor. Variaveis de gateway financeiro nao fazem parte da plataforma e devem ser rejeitadas em qualquer ambiente. Nunca usar prefixo `VITE_`, registrar o valor, copiar para documentacao ou persistir no Git.
