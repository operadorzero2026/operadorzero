CREATE TABLE team_logo (
    team_id BIGINT PRIMARY KEY REFERENCES team(id) ON DELETE CASCADE,
    content_type VARCHAR(32) NOT NULL,
    image_data BYTEA NOT NULL,
    updated_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_team_logo_content_type CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_team_logo_size CHECK (octet_length(image_data) BETWEEN 1 AND 2097152)
);
