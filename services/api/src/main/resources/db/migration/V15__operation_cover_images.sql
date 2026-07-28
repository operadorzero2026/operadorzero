CREATE TABLE operation_cover (
    operation_id BIGINT PRIMARY KEY REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    content_type VARCHAR(32) NOT NULL,
    image_data BYTEA NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    updated_by BIGINT NOT NULL REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operation_cover_content_type CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_operation_cover_size CHECK (octet_length(image_data) BETWEEN 1 AND 2097152)
);
