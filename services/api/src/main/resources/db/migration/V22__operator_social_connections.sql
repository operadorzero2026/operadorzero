CREATE TABLE operator_friendship (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    requester_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    addressee_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operator_friendship_distinct CHECK (requester_user_id <> addressee_user_id),
    CONSTRAINT ck_operator_friendship_status CHECK (status IN ('PENDING','ACCEPTED','DECLINED','CANCELLED','REMOVED'))
);

CREATE UNIQUE INDEX uq_operator_friendship_pair ON operator_friendship(
    LEAST(requester_user_id, addressee_user_id),
    GREATEST(requester_user_id, addressee_user_id)
) WHERE status IN ('PENDING','ACCEPTED');
CREATE INDEX idx_operator_friendship_requester ON operator_friendship(requester_user_id, status, updated_at DESC);
CREATE INDEX idx_operator_friendship_addressee ON operator_friendship(addressee_user_id, status, updated_at DESC);

CREATE TABLE operator_block (
    blocker_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    blocked_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(blocker_user_id, blocked_user_id),
    CONSTRAINT ck_operator_block_distinct CHECK (blocker_user_id <> blocked_user_id)
);
CREATE INDEX idx_operator_block_blocked ON operator_block(blocked_user_id, blocker_user_id);

CREATE TABLE operator_profile_report (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    reporter_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    reported_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    reason VARCHAR(40) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_operator_profile_report_distinct CHECK (reporter_user_id <> reported_user_id),
    CONSTRAINT ck_operator_profile_report_reason CHECK (reason IN ('SPAM','HARASSMENT','IMPERSONATION','FRAUD','ILLEGAL','OTHER')),
    CONSTRAINT ck_operator_profile_report_status CHECK (status IN ('OPEN','REVIEWED','DISMISSED','ACTIONED'))
);
CREATE UNIQUE INDEX uq_operator_profile_report_open
    ON operator_profile_report(reporter_user_id, reported_user_id) WHERE status = 'OPEN';
CREATE INDEX idx_operator_profile_report_queue ON operator_profile_report(status, created_at, id);

