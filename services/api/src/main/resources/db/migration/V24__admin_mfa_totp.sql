-- Verificacao em duas etapas (TOTP, RFC 6238) exigida para usar papeis administrativos.
-- A chave do autenticador fica cifrada (AES-GCM com chave derivada de AUTH_HASH_KEY); o banco nunca guarda o segredo em claro.
CREATE TABLE IF NOT EXISTS user_mfa_totp (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    secret_ciphertext TEXT NOT NULL,
    enabled_at TIMESTAMPTZ,
    last_used_step BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sessao com segundo fator confirmado. Papeis ADMIN e MODERATOR so valem em sessoes verificadas.
ALTER TABLE user_session ADD COLUMN IF NOT EXISTS mfa_verified_at TIMESTAMPTZ;
