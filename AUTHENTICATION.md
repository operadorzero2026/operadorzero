# Autenticacao

Destino: cadastro por e-mail/senha ou Google OIDC, verificacao de e-mail e recuperacao por link de uso unico. Senhas usam Argon2id; tokens de verificacao/recuperacao ficam armazenados como hash, com validade curta e invalidacao apos uso. Login, recuperacao e cadastro recebem rate limit e respostas que nao enumeram contas.

Access tokens devem ser curtos; refresh tokens rotacionados, revogaveis e associados a dispositivo/sessao. Para SPA, preferir cookie `HttpOnly`, `Secure`, `SameSite` e protecao CSRF quando a arquitetura for fechada. Administradores exigem MFA e reautenticacao para acoes criticas.

Status: modelo de usuarios existe; endpoints e provedor OIDC ainda nao foram implementados.

