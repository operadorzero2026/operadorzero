ALTER TABLE operator_profile
    ADD COLUMN preferred_position VARCHAR(48),
    ADD COLUMN recruitment_status VARCHAR(32) NOT NULL DEFAULT 'NOT_LOOKING',
    ADD CONSTRAINT ck_operator_recruitment_status
        CHECK (recruitment_status IN ('LONE_WOLF', 'LOOKING_FOR_TEAM', 'NOT_LOOKING'));

CREATE TABLE operator_position_preference (
    operator_profile_id BIGINT NOT NULL REFERENCES operator_profile(id) ON DELETE CASCADE,
    position_code VARCHAR(48) NOT NULL,
    preference_order SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (operator_profile_id, position_code),
    CONSTRAINT ck_operator_position_order CHECK (preference_order BETWEEN 1 AND 6)
);

CREATE TABLE operator_equipment (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operator_profile_id BIGINT NOT NULL REFERENCES operator_profile(id) ON DELETE CASCADE,
    category VARCHAR(48) NOT NULL,
    name VARCHAR(100) NOT NULL,
    details VARCHAR(500),
    item_condition VARCHAR(16) NOT NULL DEFAULT 'USED',
    visibility VARCHAR(32) NOT NULL DEFAULT 'ONLY_ME',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_operator_equipment_condition CHECK (item_condition IN ('NEW', 'USED')),
    CONSTRAINT ck_operator_equipment_visibility CHECK (visibility IN ('ONLY_ME', 'MY_TEAM', 'AUTHENTICATED', 'PUBLIC'))
);

CREATE INDEX idx_operator_equipment_profile ON operator_equipment(operator_profile_id, created_at DESC);
CREATE INDEX idx_operator_profile_callsign_ci ON operator_profile(lower(callsign));
CREATE INDEX idx_operator_profile_display_name_ci ON operator_profile(lower(display_name));

CREATE TABLE operator_notification_setting (
    operator_profile_id BIGINT NOT NULL REFERENCES operator_profile(id) ON DELETE CASCADE,
    notification_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (operator_profile_id, notification_code)
);

CREATE TABLE operator_username_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    previous_username VARCHAR(30) NOT NULL,
    new_username VARCHAR(30) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO operator_privacy_setting(operator_profile_id, field_code, visibility)
SELECT id, field_code, 'ONLY_ME'
FROM operator_profile
CROSS JOIN (VALUES ('LOCATION'), ('EQUIPMENT'), ('TEAM_STATUS')) AS fields(field_code)
ON CONFLICT (operator_profile_id, field_code) DO NOTHING;

