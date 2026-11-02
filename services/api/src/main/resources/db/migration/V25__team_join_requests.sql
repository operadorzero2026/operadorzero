CREATE TABLE team_join_request (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    team_id BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    message VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    responded_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_team_join_request_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_team_join_request_pending ON team_join_request(team_id, user_id) WHERE status = 'PENDING';
CREATE INDEX idx_team_join_request_team_pending ON team_join_request(team_id, created_at) WHERE status = 'PENDING';

ALTER TABLE team_membership_history DROP CONSTRAINT ck_team_history_action;
ALTER TABLE team_membership_history ADD CONSTRAINT ck_team_history_action
    CHECK (action IN ('TEAM_CREATED', 'JOINED', 'LEFT', 'ROLE_CHANGED', 'CAPTAINCY_TRANSFERRED', 'REMOVED'));
