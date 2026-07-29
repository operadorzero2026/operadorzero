# Autenticação

Status em 2026-07-29: o Operador Zero utiliza exclusivamente cadastro e login com e-mail e senha, confirmação obrigatória de e-mail, recuperação de senha e sessão opaca em cookie seguro. Autenticação social foi descontinuada.

## Contrato

- `GET /api/auth/csrf`: entrega o token CSRF e remove cookies incompatíveis de autenticação antiga.
- `POST /api/auth/register`: cria conta `PENDING_EMAIL`; exige nome, e-mail normalizado, senha, confirmação da senha e aceite.
- `POST /api/auth/verify-email`: consome token opaco, de uso único e com expiração, e ativa a conta.
- `POST /api/auth/resend-verification`: resposta neutra e novo token somente para conta pendente.
- `POST /api/auth/login`: valida Argon2id, estado e confirmação do e-mail antes de criar sessão.
- `GET /api/auth/session`: restaura somente sessão ativa e não expirada.
- `POST /api/auth/logout`: revoga a sessão atual e limpa os cookies.
- `POST /api/auth/password-recovery`: resposta não enumerável e envio do link por e-mail.
- `POST /api/auth/password-reset`: troca o hash, revoga todas as sessões do usuário e envia aviso da alteração.

Não existem endpoints, callbacks, dependências ou rotas de proxy para login social.

## Senhas, tokens e sessões

Senhas usam Argon2id. Tokens de confirmação, recuperação e sessão são aleatórios; apenas HMAC-SHA-256 com `AUTH_HASH_KEY` é persistido. Tokens são de uso único e expiram conforme `AUTH_TOKEN_DURATION`.

A sessão fica no PostgreSQL e chega ao navegador em `OZ_SESSION` com `HttpOnly`, `Secure` em produção e `SameSite=Lax`. CSRF usa cookie/header separado. Nenhuma credencial é armazenada em Web Storage, IndexedDB ou URL depois do primeiro processamento.

A migration `V17__retire_social_authentication.sql` preserva usuários e perfis, registra auditoria, revoga sessões anteriores e remove as tabelas de identidade social. Contas antigas sem senha permanecem preservadas e definem uma senha por **Esqueci minha senha**.

## E-mail

O envio transacional usa a API HTTPS da Resend. `RESEND_API_KEY` e `MAIL_FROM` pertencem somente ao backend. Os templates de confirmação, reenvio, recuperação e aviso de senha alterada usam links HTTPS baseados em `FRONTEND_BASE_URL`, texto alternativo e identidade visual do Operador Zero.

Mensagens e logs nunca incluem senha, token completo, cookie ou chave do provedor. Reenvio e recuperação aplicam rate limit e resposta neutra.

## Produção

Variáveis necessárias no Render:

- `AUTH_ENABLED=true`
- `AUTH_HASH_KEY`
- `FRONTEND_BASE_URL=https://operadorzero.com.br`
- `AUTH_COOKIE_SECURE=true`
- `AUTH_COOKIE_SAME_SITE=Lax`
- `AUTH_SESSION_DURATION`
- `AUTH_TOKEN_DURATION`
- `MAIL_ENABLED=true`
- `MAIL_FROM`
- `RESEND_API_KEY`
- PostgreSQL, Redis e `CORS_ALLOWED_ORIGINS`

A Vercel publica somente variáveis `VITE_` não sensíveis. O proxy same-origin mantém `/api` e `/actuator` antes do fallback da SPA.
