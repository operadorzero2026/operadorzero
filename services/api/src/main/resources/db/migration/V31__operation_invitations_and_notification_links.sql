-- Avisos podem abrir um endereco especifico do app (ex.: a Convocacao de um jogo), alem de uma area do painel.
ALTER TABLE user_notification ADD COLUMN target_path VARCHAR(200);

-- Convite de um amigo para um jogo: um por jogo e convidado (evita repeticao e spam).
CREATE TABLE operation_invitation (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    inviter_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    invitee_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT uq_operation_invitation UNIQUE (operation_id, invitee_user_id),
    CONSTRAINT ck_operation_invitation_status CHECK (status IN ('PENDING','ACCEPTED','DECLINED')),
    CONSTRAINT ck_operation_invitation_distinct CHECK (inviter_user_id <> invitee_user_id)
);

CREATE INDEX idx_operation_invitation_invitee ON operation_invitation(invitee_user_id, created_at DESC);
CREATE INDEX idx_operation_invitation_inviter_day ON operation_invitation(inviter_user_id, created_at DESC);
