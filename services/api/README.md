# Operador Zero API

Fundacao do backend principal. Requer Java 21, Maven, PostgreSQL e Redis.

1. Copie as variaveis de `.env.example` para seu gerenciador local.
2. Inicie dependencias com `docker compose --env-file .env -f compose.dev.yml up -d` na raiz.
3. Defina `RESEND_API_KEY` somente no ambiente do backend e um remetente verificado em `MAIL_FROM`, ou use `MAIL_ENABLED=false` apenas em testes tecnicos.
4. Execute `mvn spring-boot:run` nesta pasta.
5. Verifique `/actuator/health` e `GET /api/auth/csrf`.

Identidade funcional: cadastro, verificacao de e-mail, login, sessao, logout, recuperacao e Google OIDC. O frontend deve usar `credentials: include`, obter CSRF antes de cada mutacao e nunca armazenar credenciais/tokens em Web Storage.

As rotas de negocio continuam negadas por padrao e os modulos exibidos no dashboard ainda sao demonstrativos. Para staging, configure `AUTH_ENABLED`, `FRONTEND_BASE_URL`, Resend, CORS e cookies; Google exige suas proprias credenciais.
