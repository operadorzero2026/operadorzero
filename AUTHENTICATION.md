# Autenticacao

Status em 2026-07-23: cadastro por e-mail/senha, confirmacao de e-mail, login, logout, recuperacao, troca de senha e Google OIDC estao implementados na API e conectados a SPA. No staging, cadastro Google, callback autenticado e criacao da sessao opaca foram validados E2E com conta de teste.

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

O handshake Google usa uma sessao temporaria separada (`OZ_OAUTH_SESSION`), `HttpOnly`, `Secure`, `SameSite=Lax` e com expiracao de 10 minutos. Em producao, essa sessao e persistida em Redis no namespace `operador-zero:oauth-session`, preservando `state`, `nonce` e o verificador PKCE durante reinicios ou troca de instancia. Falhas registram somente o codigo tecnico sanitizado e a classe da excecao, sem codigo de autorizacao, token, segredo ou descricao do provedor.

Durante a inicializacao da API, o botao Google informa explicitamente o progresso. Se o usuario cancelar o provedor ou voltar pelo navegador, o evento `pageshow` limpa o estado pendente restaurado pelo cache de navegacao e permite uma nova tentativa.

Cadastro, login, recuperacao e OIDC recebem rate limit por IP e sujeito no Redis. Falha do Redis fecha o fluxo de autenticacao, em vez de remover o limite. E-mail e Google dependem de configuracao externa; nenhuma credencial pertence ao frontend.

O envio transacional usa a API HTTPS da Resend com `Idempotency-Key`; `RESEND_API_KEY` existe somente no backend. Falhas do provedor sao registradas sem destinatario em claro, corpo da resposta ou chave. O dominio `mail.operadorzero.com.br` esta verificado e os e-mails de confirmacao e recuperacao foram entregues no E2E externo.

Pendencias antes de usuarios reais: DMARC e tratamento de bounce, remocao do segredo Google anterior depois da homologacao concluida, MFA administrativo, reautenticacao critica, central de sessoes, termos/privacidade aprovados e infraestrutura sem expiracao.
