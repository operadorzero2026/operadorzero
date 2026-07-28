CREATE TABLE operator_profile_photo (
    operator_profile_id BIGINT PRIMARY KEY REFERENCES operator_profile(id) ON DELETE CASCADE,
    content_type VARCHAR(32) NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg')),
    image_data BYTEA NOT NULL CHECK (octet_length(image_data) BETWEEN 1 AND 2097152),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
