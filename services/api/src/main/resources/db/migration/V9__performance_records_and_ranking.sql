CREATE TABLE performance_record (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE RESTRICT,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    represented_team_id BIGINT REFERENCES team(id) ON DELETE SET NULL,
    eliminations INTEGER NOT NULL DEFAULT 0,
    deaths INTEGER NOT NULL DEFAULT 0,
    objectives_completed INTEGER NOT NULL DEFAULT 0,
    round_wins INTEGER NOT NULL DEFAULT 0,
    result VARCHAR(12) NOT NULL DEFAULT 'NONE',
    position_used VARCHAR(48),
    notes VARCHAR(1000),
    highlight_received VARCHAR(160),
    penalty_points NUMERIC(10,2) NOT NULL DEFAULT 0,
    abandoned BOOLEAN NOT NULL DEFAULT FALSE,
    participation_scope VARCHAR(12) NOT NULL DEFAULT 'FULL',
    status VARCHAR(32) NOT NULL DEFAULT 'SELF_DECLARED',
    organizer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    team_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    organizer_notes VARCHAR(1000),
    ranking_rule_version VARCHAR(32) NOT NULL DEFAULT 'OZ-RANK-2026-07-28',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_performance_operation_user UNIQUE(operation_id, user_id),
    CONSTRAINT ck_performance_non_negative CHECK (eliminations >= 0 AND deaths >= 0 AND objectives_completed >= 0 AND round_wins >= 0 AND penalty_points >= 0),
    CONSTRAINT ck_performance_result CHECK (result IN ('WIN','LOSS','DRAW','NONE')),
    CONSTRAINT ck_performance_scope CHECK (participation_scope IN ('FULL','PARTIAL')),
    CONSTRAINT ck_performance_status CHECK (status IN ('PENDING','SELF_DECLARED','ORGANIZER_CONFIRMED','TEAM_CONFIRMED','CONTESTED','CORRECTED','REJECTED'))
);

CREATE INDEX idx_performance_user ON performance_record(user_id, updated_at DESC);
CREATE INDEX idx_performance_operation ON performance_record(operation_id, status);
CREATE INDEX idx_performance_ranking_valid ON performance_record(user_id, ranking_rule_version)
    WHERE status <> 'REJECTED';

CREATE TABLE performance_contest (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    performance_record_id BIGINT NOT NULL REFERENCES performance_record(id) ON DELETE CASCADE,
    contestant_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_performance_contest_status CHECK (status IN ('OPEN','ACCEPTED','REJECTED','RESOLVED'))
);

CREATE UNIQUE INDEX uq_performance_contest_open_user
    ON performance_contest(performance_record_id, contestant_user_id) WHERE status = 'OPEN';
