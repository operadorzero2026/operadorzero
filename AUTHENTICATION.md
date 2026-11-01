# Autenticação

Status em 2026-11-01: cadastro e login com e-mail e senha (confirmação obrigatória do e-mail, recuperação de senha e sessão opaca em cookie seguro) e **login e cadastro com Google** (OpenID Connect), que voltou a ser requisito. O Google é ligado por `GOOGLE_AUTH_ENABLED`.

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
- `POST /api/auth/google/start` (CSRF, rate limit de 10 por 15 min): `mode` = `login`, `register` (exige aceite dos termos) ou `link` (exige sessão). Libera um único pedido de autorização e devolve `/oauth2/authorization/google`.
- `GET /oauth2/authorization/google`: redireciona ao Google com `state`, `nonce` e PKCE (S256) somente se o fluxo foi liberado; acesso direto ou repetido volta ao site com `GOOGLE_FLOW_EXPIRED`.
- `GET /login/oauth2/code/google`: callback. O Spring Security troca o código e valida o ID token (assinatura JWKS, `iss`, `aud`, `exp`, `nonce`) e o `state`.
- `DELETE /api/auth/google/link`: desvincula o Google da conta logada; exige que a conta tenha senha.
- A sessão (`/session` e `/login`) informa `googleLinked` e `hasPassword`.

## Login com Google

A conta Google é reconhecida somente pelo par imutável `(issuer, subject)` em `user_oidc_identity` (migration `V23`). **E-mail igual nunca vincula contas automaticamente.** Depois do callback, o servidor devolve a pessoa a `/?auth=google-STATUS&code=CODE`:

| Situação | Resultado |
|---|---|
| `(iss, sub)` já vinculado e conta ativa | Sessão criada (`google-success`). |
| E-mail do Google não verificado | `google-error`, `GOOGLE_EMAIL_NOT_VERIFIED`. |
| E-mail já existe em outra conta, sem vínculo | Nada é vinculado. Uma intenção de vínculo fica 15 min no Redis (cookie `OZ_GOOGLE_LINK`) e a pessoa precisa **entrar com a senha daquela conta** no mesmo navegador; só então o vínculo é gravado e auditado (`AUTH_GOOGLE_LINKED`). |
| Conta inexistente, modo `register` com aceite | Conta `ACTIVE` sem senha, vínculo e sessão (`AUTH_REGISTER_GOOGLE`). |
| Conta inexistente, modo `login` | `google-signup-required`: a interface pede o aceite dos termos. |
| Modo `link` (pessoa logada em Meu Operador) | Vincula à conta da sessão atual, conferida de novo no callback; recusa identidade de outra conta (`GOOGLE_ALREADY_LINKED`). |
| Cancelamento ou negação no Google | `GOOGLE_CANCELLED`. |
| `state` ausente, inválido ou expirado | `GOOGLE_FLOW_EXPIRED`. |

O estado temporário (pedido de autorização com `state`, `nonce` e `code_verifier`, liberação de uso único e intenção de vínculo) fica no Redis com TTL de 10 a 15 minutos; o navegador guarda apenas tokens opacos em cookies `HttpOnly`, `Secure` e `SameSite=Lax`. Não há sessão HTTP. O client secret fica somente no `.env` do servidor e nunca usa prefixo `VITE_`. Contas criadas pelo Google não têm senha; podem definir uma por **Esqueci minha senha**.

## Verificação em duas etapas (MFA)

- `POST /api/auth/mfa/setup`: gera a chave TOTP (cifrada no banco com AES-GCM, chave derivada de `AUTH_HASH_KEY`) e devolve a chave e o `otpauth://` uma única vez.
- `POST /api/auth/mfa/enable`, `/verify` e `/disable` recebem o código de 6 dígitos (rate limit de 6 por 15 min; cada código vale uma vez).
- Papéis `ADMIN` e `MODERATOR` só valem em sessões que confirmaram o código; nas demais, o filtro de sessão os remove e a sessão informa `mfaRequired`.

## Senhas, tokens e sessões

Senhas usam Argon2id. Tokens de confirmação, recuperação e sessão são aleatórios; apenas HMAC-SHA-256 com `AUTH_HASH_KEY` é persistido. Tokens são de uso único e expiram conforme `AUTH_TOKEN_DURATION`.

A sessão fica no PostgreSQL e chega ao navegador em `OZ_SESSION` com `HttpOnly`, `Secure` em produção e `SameSite=Lax`. CSRF usa cookie/header separado. Nenhuma credencial é armazenada em Web Storage, IndexedDB ou URL depois do primeiro processamento.

A migration `V17__retire_social_authentication.sql` (2026-07-29) removeu as tabelas da primeira versão do login social; a `V23__restore_google_identity.sql` recria somente a identidade, com chave `(issuer, subject)`. Contas sem senha definem uma por **Esqueci minha senha**.

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

## Desempenho e observabilidade

Ao abrir qualquer formulário de autenticação, o frontend prepara o CSRF de forma idempotente e compartilhada, antecipando a retomada da API sem criar keep-alive periódico. O login mede no navegador as fases `csrf`, `login-request` e `login-to-usable` com `performance.mark/measure`, sem dados pessoais.

O backend emite o evento estruturado `auth_login_duration` com `correlationId`, resultado técnico e tempos de Redis, consulta do usuário, Argon2id, sessão, cookie, auditoria e total. E-mail, senha, hash, token e cookie nunca são incluídos. Consulte [[20-DIAGNOSTICO-DE-LATENCIA-DO-LOGIN-2026-07-29]] no vault.
