-- Restaura o login com Google. A identidade e reconhecida somente pelo par imutavel (issuer, subject);
-- o e-mail fica apenas como registro de quando o vinculo foi feito e nunca e usado para associar contas.
-- O estado temporario do OAuth (state, nonce, PKCE, intencao de vinculo) fica no Redis com TTL.
CREATE TABLE IF NOT EXISTS user_oidc_identity (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL CHECK (provider IN ('google')),
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email_at_link VARCHAR(254) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uq_user_oidc_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT uq_user_oidc_provider_user UNIQUE (provider, user_id)
);
