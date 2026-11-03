-- Chat geral da operacao: comunicados fixados e silenciamento por tempo (1 h, 6 h, 24 h ou indeterminado).
ALTER TABLE operation_chat_message
    ADD COLUMN pinned_at TIMESTAMPTZ,
    ADD COLUMN pinned_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL;
CREATE INDEX idx_operation_chat_pinned ON operation_chat_message(channel_id, pinned_at DESC) WHERE pinned_at IS NOT NULL;
CREATE INDEX idx_operation_chat_recent ON operation_chat_message(channel_id, created_at DESC, id DESC);

-- Silenciado continua lendo, mas nao envia. until nulo = tempo indeterminado; lifted_at = silenciamento retirado.
CREATE TABLE operation_chat_mute (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    until TIMESTAMPTZ,
    created_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lifted_at TIMESTAMPTZ,
    lifted_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE INDEX idx_operation_chat_mute ON operation_chat_mute(operation_id, user_id, created_at DESC);
