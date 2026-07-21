CREATE TABLE operator_profile (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE RESTRICT,
    callsign VARCHAR(40) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    real_name_encrypted BYTEA,
    bio VARCHAR(500),
    city VARCHAR(80),
    state_code CHAR(2),
    country_code CHAR(2) NOT NULL DEFAULT 'BR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE operator_privacy_setting (
    operator_profile_id BIGINT NOT NULL REFERENCES operator_profile(id) ON DELETE CASCADE,
    field_code VARCHAR(64) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (operator_profile_id, field_code),
    CONSTRAINT ck_operator_privacy_visibility CHECK (visibility IN ('ONLY_ME','MY_TEAM','RELATED_ORGANIZERS','AUTHENTICATED','PUBLIC'))
);

CREATE INDEX idx_operator_profile_city_state ON operator_profile(state_code, city);
