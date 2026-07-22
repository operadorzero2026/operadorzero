# Desenvolvimento local

## Requisitos

- Node.js 22 e npm.
- Java 21 e Maven 3.9+.
- PostgreSQL 17 e Redis 7, ou Docker Compose.

## Configuracao

1. Copie `.env.example` para `.env.local` e defina apenas valores locais.
2. Para o Vite, mantenha somente `VITE_API_URL`; toda variavel `VITE_*` e publica no navegador.
3. Inicie PostgreSQL e Redis com `docker compose -f compose.dev.yml up -d` ou servicos locais equivalentes.
4. Inicie a API em `services/api` com `mvn spring-boot:run`.
5. Inicie a web com `npm run dev -- --port 4174`.

O botao `Entrar no modo demonstracao local` existe somente em build de desenvolvimento. Login, cadastro e recuperacao normais usam a API configurada e nao persistem credenciais no navegador.

Antes de enviar alteracoes, execute `npm run check` e `mvn clean verify` em `services/api`.
