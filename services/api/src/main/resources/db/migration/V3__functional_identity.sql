CREATE TABLE auth_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_token_purpose CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD'))
);

CREATE INDEX idx_auth_token_user_purpose ON auth_token(user_id, purpose, expires_at DESC);

CREATE TABLE user_session (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    user_agent_hash CHAR(64),
    ip_prefix_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_user_session_active ON user_session(user_id, expires_at DESC) WHERE revoked_at IS NULL;

CREATE TABLE user_oidc_identity (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email_at_link VARCHAR(254) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_oidc_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT uq_user_oidc_provider_user UNIQUE (provider, user_id)
);

CREATE TABLE oauth_registration_intent (
    id BIGSERIAL PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    terms_version VARCHAR(32) NOT NULL,
    privacy_version VARCHAR(32) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

INSERT INTO role(code, description) VALUES
    ('USER', 'Operador autenticado'),
    ('TEAM_MANAGER', 'Gestor de equipe'),
    ('ORGANIZER', 'Organizador de operacao'),
    ('MODERATOR', 'Moderador de conteudo'),
    ('FINANCE_MANAGER', 'Gestor financeiro'),
    ('ADMIN', 'Administrador da plataforma')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission(code, description) VALUES
    ('PROFILE_READ_SELF', 'Ler o proprio perfil'),
    ('PROFILE_UPDATE_SELF', 'Atualizar o proprio perfil'),
    ('SESSION_REVOKE_SELF', 'Revogar as proprias sessoes')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('PROFILE_READ_SELF','PROFILE_UPDATE_SELF','SESSION_REVOKE_SELF')
WHERE r.code = 'USER'
ON CONFLICT DO NOTHING;
