# Variaveis de ambiente

Modelo local em `.env.example`. Na API de staging sao obrigatorias: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_URL`, `CORS_ALLOWED_ORIGINS` e `SPRING_PROFILES_ACTIVE`. `PORT` e fornecida pelo Render. No frontend, somente `VITE_API_URL` e permitida e seu valor e publico.

Producao usa cofre de segredos, rotacao, identidade por workload quando disponivel e acesso minimo. Valores reais nao entram em `.env`, compose, logs, imagens ou repositorio. Variaveis publicas do Vite jamais guardam segredo.

Chaves OIDC/JWT, S3, e-mail, Mercado Pago e telemetria somente serao documentadas quando os adaptadores correspondentes existirem no backend. Nao criar variaveis ficticias no frontend.
