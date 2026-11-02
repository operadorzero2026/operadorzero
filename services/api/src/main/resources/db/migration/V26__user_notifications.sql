CREATE TABLE user_notification (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    kind VARCHAR(48) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500),
    target_view VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_user_notification_feed ON user_notification(user_id, created_at DESC, id DESC);
CREATE INDEX idx_user_notification_unread ON user_notification(user_id) WHERE read_at IS NULL;
