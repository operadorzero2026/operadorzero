# Variaveis de ambiente

Modelo local em `.env.example`. Na API de staging sao obrigatorias: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_URL`, `CORS_ALLOWED_ORIGINS` e `SPRING_PROFILES_ACTIVE`. Para identidade real tambem sao obrigatorias `AUTH_HASH_KEY`, `RESEND_API_KEY`, `MAIL_FROM`, `MAIL_ENABLED=true`, `AUTH_ENABLED=true` e `FRONTEND_BASE_URL`. `AUTH_HASH_KEY` deve ser aleatoria, exclusiva por ambiente, possuir ao menos 32 bytes e permanecer estavel entre reinicios. `PORT` e fornecida pelo Render. No frontend, somente variaveis publicas `VITE_*` podem existir.

Producao usa cofre de segredos, rotacao, identidade por workload quando disponivel e acesso minimo. Valores reais nao entram em `.env`, compose, logs, imagens ou repositorio. Variaveis publicas do Vite jamais guardam segredo.

`AUTH_HASH_KEY`, `RESEND_API_KEY` e qualquer chave S3 ou de telemetria pertencem somente ao backend e ao cofre do provedor. A troca de `AUTH_HASH_KEY` invalida sessoes e links de autenticacao existentes e exige janela operacional planejada. Variaveis de gateway financeiro nao fazem parte da plataforma e devem ser rejeitadas em qualquer ambiente. Nunca usar prefixo `VITE_`, registrar o valor, copiar para documentacao ou persistir no Git.

No frontend oficial, `VITE_API_URL` deve ficar ausente ou apontar para `https://operadorzero.com.br`; o proxy da Vercel encaminha a API sem transformar a sessao em cookie de terceiro. Variaveis antigas de autenticacao social devem ser removidas do Render e nao possuem substitutas.
