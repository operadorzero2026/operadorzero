-- Preserve users and their non-authentication data while retiring social login.
-- Existing users without a password remain ACTIVE and can define one through
-- the password-recovery flow because their verified e-mail is retained.

INSERT INTO audit_event(
    actor_user_id, action, entity_type, entity_public_id,
    correlation_id, occurred_at
)
SELECT
    oi.user_id,
    'AUTH_SOCIAL_LOGIN_RETIRED',
    'APP_USER',
    u.public_id,
    'migration-v17-' || u.public_id,
    now()
FROM user_oidc_identity oi
JOIN app_user u ON u.id = oi.user_id;

-- Authentication persistence changed. Revoke every pre-migration session so
-- no session created by the retired flow survives the release.
UPDATE user_session
SET revoked_at = now()
WHERE revoked_at IS NULL;

DROP TABLE IF EXISTS oauth_registration_intent;
DROP TABLE IF EXISTS user_oidc_identity;
