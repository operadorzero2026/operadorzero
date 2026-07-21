# Variaveis de ambiente

Modelo local em `.env.example`. Obrigatorias na API: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` e `SPRING_PROFILES_ACTIVE`. Futuras: chaves OIDC/JWT, S3, e-mail, Mercado Pago e destinos de telemetria.

Producao usa cofre de segredos, rotacao, identidade por workload quando disponivel e acesso minimo. Valores reais nao entram em `.env`, compose, logs, imagens ou repositorio. Variaveis publicas do Vite jamais guardam segredo.

