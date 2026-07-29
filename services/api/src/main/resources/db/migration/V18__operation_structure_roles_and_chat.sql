ALTER TABLE airsoft_operation
    ADD COLUMN game_size VARCHAR(8),
    ADD COLUMN command_roles_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN allow_role_accumulation BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN general_chat_public_read BOOLEAN NOT NULL DEFAULT FALSE,
    ALTER COLUMN participant_limit DROP NOT NULL;

UPDATE airsoft_operation o
SET game_size = CASE
    WHEN COALESCE(o.participant_limit, 0) <= 50
         AND (SELECT count(*) FROM operation_team t WHERE t.operation_id = o.id) = 2 THEN 'SMALL'
    WHEN COALESCE(o.participant_limit, 0) <= 100
         AND (SELECT count(*) FROM operation_team t WHERE t.operation_id = o.id) <= 4 THEN 'MEDIUM'
    ELSE 'LARGE'
END,
command_roles_enabled = CASE WHEN COALESCE(o.participant_limit, 0) > 50 THEN TRUE ELSE FALSE END;

ALTER TABLE airsoft_operation
    ALTER COLUMN game_size SET NOT NULL,
    ADD CONSTRAINT ck_operation_game_size CHECK (game_size IN ('SMALL','MEDIUM','LARGE')),
    ADD CONSTRAINT ck_operation_size_limit CHECK (
        (game_size = 'SMALL' AND participant_limit BETWEEN 1 AND 50)
        OR (game_size = 'MEDIUM' AND participant_limit BETWEEN 1 AND 100)
        OR (game_size = 'LARGE' AND (participant_limit IS NULL OR participant_limit > 0))
    ),
    ADD CONSTRAINT ck_small_without_command_roles CHECK (game_size <> 'SMALL' OR command_roles_enabled = FALSE);

ALTER TABLE operation_team
    ADD COLUMN acronym VARCHAR(12),
    ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#6f7839',
    ADD COLUMN description VARCHAR(1000),
    ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_operation_team_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    ADD CONSTRAINT ck_operation_team_status CHECK (status IN ('OPEN','CLOSED'));

CREATE TABLE operation_squad (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_team_id BIGINT NOT NULL REFERENCES operation_team(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    acronym VARCHAR(12),
    description VARCHAR(1000),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (operation_team_id, sort_order)
);

ALTER TABLE operation_participant
    ADD COLUMN operation_squad_id BIGINT REFERENCES operation_squad(id) ON DELETE SET NULL;

CREATE TABLE operation_role_assignment (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    operation_team_id BIGINT REFERENCES operation_team(id) ON DELETE CASCADE,
    operation_squad_id BIGINT REFERENCES operation_squad(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL CHECK (role IN ('OPERATION_ADMIN','TEAM_COMMANDER','TEAM_RADIO','SQUAD_COMMANDER','SQUAD_RADIO')),
    assigned_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operation_role_scope CHECK (
        (role = 'OPERATION_ADMIN' AND operation_team_id IS NULL AND operation_squad_id IS NULL)
        OR (role IN ('TEAM_COMMANDER','TEAM_RADIO') AND operation_team_id IS NOT NULL AND operation_squad_id IS NULL)
        OR (role IN ('SQUAD_COMMANDER','SQUAD_RADIO') AND operation_team_id IS NOT NULL AND operation_squad_id IS NOT NULL)
    ),
    UNIQUE NULLS NOT DISTINCT (operation_id, role, operation_team_id, operation_squad_id)
);

CREATE TABLE operation_chat_channel (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    operation_team_id BIGINT REFERENCES operation_team(id) ON DELETE CASCADE,
    channel_type VARCHAR(12) NOT NULL CHECK (channel_type IN ('GENERAL','TEAM')),
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','LOCKED')),
    public_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operation_channel_scope CHECK (
        (channel_type = 'GENERAL' AND operation_team_id IS NULL)
        OR (channel_type = 'TEAM' AND operation_team_id IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_operation_general_channel ON operation_chat_channel(operation_id) WHERE channel_type = 'GENERAL';
CREATE UNIQUE INDEX uq_operation_team_channel ON operation_chat_channel(operation_team_id) WHERE channel_type = 'TEAM';

CREATE FUNCTION create_operation_general_chat_channel() RETURNS trigger AS $$
BEGIN
    INSERT INTO operation_chat_channel(operation_id, channel_type, public_read)
    VALUES (NEW.id, 'GENERAL', NEW.general_chat_public_read)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operation_general_chat_channel
AFTER INSERT ON airsoft_operation FOR EACH ROW EXECUTE FUNCTION create_operation_general_chat_channel();

CREATE FUNCTION create_operation_team_chat_channel() RETURNS trigger AS $$
BEGIN
    INSERT INTO operation_chat_channel(operation_id, operation_team_id, channel_type)
    VALUES (NEW.operation_id, NEW.id, 'TEAM')
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operation_team_chat_channel
AFTER INSERT ON operation_team FOR EACH ROW EXECUTE FUNCTION create_operation_team_chat_channel();

CREATE TABLE operation_chat_message (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    channel_id BIGINT NOT NULL REFERENCES operation_chat_channel(id) ON DELETE CASCADE,
    author_user_id BIGINT NOT NULL REFERENCES app_user(id),
    parent_message_id BIGINT REFERENCES operation_chat_message(id),
    body VARCHAR(2000) NOT NULL CHECK (length(trim(body)) > 0),
    status VARCHAR(12) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE','EDITED','DELETED','HIDDEN')),
    official BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (author_user_id, idempotency_key)
);

CREATE TABLE operation_chat_message_report (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    message_id BIGINT NOT NULL REFERENCES operation_chat_message(id) ON DELETE CASCADE,
    reporter_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','REVIEWED','DISMISSED','ACTIONED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by BIGINT REFERENCES app_user(id),
    UNIQUE (message_id, reporter_user_id)
);

CREATE TABLE operation_moderation_action (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    actor_user_id BIGINT NOT NULL REFERENCES app_user(id),
    target_type VARCHAR(24) NOT NULL,
    target_public_id UUID,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO operation_chat_channel(operation_id, channel_type, public_read)
SELECT id, 'GENERAL', general_chat_public_read FROM airsoft_operation
ON CONFLICT DO NOTHING;

INSERT INTO operation_chat_channel(operation_id, operation_team_id, channel_type)
SELECT operation_id, id, 'TEAM' FROM operation_team
ON CONFLICT DO NOTHING;

CREATE INDEX idx_operation_game_size ON airsoft_operation(game_size);
CREATE INDEX idx_operation_team_operation_status ON operation_team(operation_id, status, sort_order);
CREATE INDEX idx_operation_squad_team_status ON operation_squad(operation_team_id, status, sort_order);
CREATE INDEX idx_operation_participant_squad ON operation_participant(operation_squad_id, status);
CREATE INDEX idx_operation_role_user ON operation_role_assignment(operation_id, user_id);
CREATE INDEX idx_operation_role_team ON operation_role_assignment(operation_team_id, role);
CREATE INDEX idx_operation_role_squad ON operation_role_assignment(operation_squad_id, role);
CREATE INDEX idx_operation_chat_message_page ON operation_chat_message(channel_id, created_at DESC, id DESC);
CREATE INDEX idx_operation_chat_report_status ON operation_chat_message_report(status, created_at);
CREATE INDEX idx_operation_moderation_operation ON operation_moderation_action(operation_id, created_at DESC);
