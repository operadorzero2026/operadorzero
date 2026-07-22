CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    username VARCHAR(30) NOT NULL,
    password_hash VARCHAR(255),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_EMAIL',
    email_verified_at TIMESTAMPTZ,
    terms_version VARCHAR(32) NOT NULL,
    terms_accepted_at TIMESTAMPTZ NOT NULL,
    privacy_version VARCHAR(32) NOT NULL,
    privacy_accepted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_user_public_id UNIQUE (public_id),
    CONSTRAINT ck_app_user_status CHECK (status IN ('PENDING_EMAIL','ACTIVE','LOCKED','SUSPENDED','DELETED'))
);

CREATE UNIQUE INDEX uq_app_user_email_ci ON app_user (lower(email));
CREATE UNIQUE INDEX uq_app_user_username_ci ON app_user (lower(username));

CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(160) NOT NULL
);

CREATE TABLE permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(180) NOT NULL
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role(id) ON DELETE RESTRICT,
    granted_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permission (
    role_id BIGINT NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE audit_event (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    actor_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_public_id UUID,
    reason VARCHAR(500),
    correlation_id VARCHAR(80) NOT NULL,
    session_hash VARCHAR(128),
    ip_prefix_hash VARCHAR(128),
    before_data JSONB,
    after_data JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_event_entity ON audit_event(entity_type, entity_public_id, occurred_at DESC);
CREATE INDEX idx_audit_event_actor ON audit_event(actor_user_id, occurred_at DESC);
