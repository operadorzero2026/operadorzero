# Arquitetura do backend

API principal: Java 21 + Spring Boot 3.5, em `services/api`. O desenho e monolito modular: controller apenas traduz HTTP; application/service orquestra casos de uso; dominio concentra regras; repositories/adapters isolam PostgreSQL, Redis, S3, e-mail e pagamentos.

Regras transversais: identificador do usuario vem do token validado; autorizacao e checada por acao e objeto; entrada usa DTO + Bean Validation; consultas parametrizadas; migrations Flyway; erros nao expõem stack trace; toda mutacao sensivel gera auditoria; idempotency key em comandos externos; paginacao e limites em colecoes.

O scaffold atual expoe somente health/OpenAPI e nega por padrao o restante. Isso e intencional ate a implementacao de identidade.

