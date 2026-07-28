CREATE TABLE team (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    name VARCHAR(80) NOT NULL,
    acronym VARCHAR(12) NOT NULL,
    city VARCHAR(80) NOT NULL,
    state_code CHAR(2) NOT NULL,
    game_style VARCHAR(80) NOT NULL,
    owns_field BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(500),
    recruitment_status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_team_recruitment_status CHECK (recruitment_status IN ('OPEN', 'INVITE_ONLY', 'CLOSED')),
    CONSTRAINT ck_team_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_team_active_name_ci ON team(lower(name)) WHERE status = 'ACTIVE';
CREATE INDEX idx_team_location ON team(state_code, city) WHERE status = 'ACTIVE';

CREATE TABLE team_member (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    role VARCHAR(24) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    invited_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_team_member_role CHECK (role IN ('CAPTAIN', 'MANAGER', 'MEMBER'))
);

CREATE UNIQUE INDEX uq_team_member_active_user ON team_member(user_id) WHERE left_at IS NULL;
CREATE UNIQUE INDEX uq_team_active_captain ON team_member(team_id) WHERE left_at IS NULL AND role = 'CAPTAIN';
CREATE INDEX idx_team_member_team_active ON team_member(team_id, joined_at) WHERE left_at IS NULL;

CREATE TABLE team_invitation (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    team_id BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    invitee_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    inviter_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    proposed_role VARCHAR(24) NOT NULL DEFAULT 'MEMBER',
    message VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT ck_team_invitation_role CHECK (proposed_role IN ('MANAGER', 'MEMBER')),
    CONSTRAINT ck_team_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'EXPIRED'))
);

CREATE UNIQUE INDEX uq_team_invitation_pending
    ON team_invitation(team_id, invitee_user_id) WHERE status = 'PENDING';
CREATE INDEX idx_team_invitation_received ON team_invitation(invitee_user_id, created_at DESC);

CREATE TABLE team_membership_history (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    action VARCHAR(40) NOT NULL,
    previous_role VARCHAR(24),
    new_role VARCHAR(24),
    actor_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_team_history_action CHECK (action IN ('TEAM_CREATED', 'JOINED', 'LEFT', 'ROLE_CHANGED', 'CAPTAINCY_TRANSFERRED'))
);

CREATE INDEX idx_team_membership_history_team ON team_membership_history(team_id, occurred_at DESC);

