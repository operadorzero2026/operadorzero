# Operador Zero API

Fundacao do backend principal. Requer Java 21, Maven, PostgreSQL e Redis.

1. Copie as variaveis de `.env.example` para seu gerenciador local.
2. Inicie dependencias com `docker compose --env-file .env -f compose.dev.yml up -d` na raiz.
3. Execute `mvn spring-boot:run` nesta pasta.
4. Verifique `/actuator/health`.

O scaffold ainda nao oferece login nem endpoints de negocio. Por seguranca, todas as rotas fora de health/OpenAPI sao negadas ate a Fase 2.
