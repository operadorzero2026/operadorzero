# Autenticacao

Status em 2026-07-22: cadastro por e-mail/senha, confirmacao de e-mail, login, logout, recuperacao, troca de senha e Google OIDC estao implementados na API e conectados a SPA.

## Contrato

- `GET /api/auth/csrf`: entrega token CSRF para o header indicado.
- `POST /api/auth/register`: cria conta pendente e envia confirmacao sem enumerar contas.
- `POST /api/auth/verify-email`: consome token opaco uma unica vez e ativa a conta.
- `POST /api/auth/login`: valida Argon2id e cria sessao opaca.
- `GET /api/auth/session`: restaura a identidade autenticada.
- `POST /api/auth/logout`: revoga a sessao atual e limpa o cookie.
- `POST /api/auth/password-recovery`: resposta uniforme e envio do link por e-mail.
- `POST /api/auth/password-reset`: altera a senha e revoga sessoes anteriores.
- `POST /api/auth/google/intent` e `/oauth2/authorization/google`: OIDC Authorization Code com PKCE, `state` e `nonce` gerenciados pelo Spring Security.

Senhas usam Argon2id. Tokens de verificacao, recuperacao, sessao e aceite OIDC sao aleatorios; somente SHA-256 e persistido. A sessao fica em cookie `HttpOnly`; em producao usa `Secure` e `SameSite=None` para a SPA hospedada em outro dominio. CSRF usa cookie/header separado e a SPA renova o token depois do login.

Cadastro, login, recuperacao e OIDC recebem rate limit por IP e sujeito no Redis. Falha do Redis fecha o fluxo de autenticacao, em vez de remover o limite. E-mail e Google dependem de configuracao externa; nenhuma credencial pertence ao frontend.

O envio transacional usa a API HTTPS da Resend com `Idempotency-Key`; `RESEND_API_KEY` existe somente no backend. Falhas do provedor sao registradas sem destinatario em claro, corpo da resposta ou chave. O remetente depende de dominio verificado.

Pendencias antes de usuarios reais: dominio Resend homologado com SPF/DKIM/DMARC e tratamento de bounce, credenciais Google por ambiente, MFA administrativo, reautenticacao critica, central de sessoes, teste E2E no staging e termos/privacidade aprovados.
