CREATE INDEX idx_oauth_registration_intent_cleanup
    ON oauth_registration_intent(expires_at, consumed_at);
