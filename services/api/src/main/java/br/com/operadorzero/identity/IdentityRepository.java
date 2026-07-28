package br.com.operadorzero.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    private static final String ACCOUNT_SELECT = """
        SELECT u.id, u.public_id, u.email, u.username, u.password_hash, u.status,
               p.display_name, p.callsign
        FROM app_user u
        JOIN operator_profile p ON p.user_id = u.id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public IdentityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return single(ACCOUNT_SELECT + " WHERE lower(u.email) = lower(:email)", Map.of("email", email));
    }

    public Optional<UserAccount> findById(long userId) {
        return single(ACCOUNT_SELECT + " WHERE u.id = :id", Map.of("id", userId));
    }

    public Optional<UserAccount> findBySessionHash(String tokenHash, Instant now) {
        return single(ACCOUNT_SELECT + """
             JOIN user_session s ON s.user_id = u.id
             WHERE s.token_hash = :tokenHash
               AND s.revoked_at IS NULL
               AND s.expires_at > :now
               AND u.status = 'ACTIVE'
            """, Map.of("tokenHash", tokenHash, "now", dbTime(now)));
    }

    public Optional<UserAccount> findByOidc(String issuer, String subject) {
        return single(ACCOUNT_SELECT + """
             JOIN user_oidc_identity oi ON oi.user_id = u.id
             WHERE oi.issuer = :issuer AND oi.subject = :subject
            """, Map.of("issuer", issuer, "subject", subject));
    }

    private Optional<UserAccount> single(String sql, Map<String, ?> parameters) {
        try {
            UserAccount base = jdbc.queryForObject(sql, parameters, this::mapAccountWithoutRoles);
            return Optional.of(withRoles(base));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private UserAccount mapAccountWithoutRoles(ResultSet rs, int row) throws SQLException {
        return new UserAccount(
            rs.getLong("id"),
            rs.getObject("public_id", UUID.class),
            rs.getString("email"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getString("display_name"),
            rs.getString("callsign"),
            List.of()
        );
    }

    private UserAccount withRoles(UserAccount account) {
        List<String> roles = jdbc.queryForList("""
            SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId ORDER BY r.code
            """, Map.of("userId", account.id()), String.class);
        return new UserAccount(account.id(), account.publicId(), account.email(), account.username(), account.passwordHash(),
            account.status(), account.displayName(), account.callsign(), List.copyOf(roles));
    }

    public boolean usernameExists(String username) {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM app_user WHERE lower(username) = lower(:username))",
            Map.of("username", username), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public long createUser(String email, String username, String passwordHash, String status,
                           String displayName, String callsign, String termsVersion, String privacyVersion, Instant now) {
        List<Long> userIds = jdbc.queryForList("""
            INSERT INTO app_user(email, username, password_hash, status, email_verified_at,
                                 terms_version, terms_accepted_at, privacy_version, privacy_accepted_at,
                                 created_at, updated_at)
            VALUES (:email, :username, :passwordHash, :status,
                    CASE WHEN :status = 'ACTIVE' THEN CAST(:now AS TIMESTAMPTZ) ELSE NULL END,
                    :termsVersion, :now, :privacyVersion, :now, :now, :now)
            ON CONFLICT DO NOTHING
            RETURNING id
            """, new MapSqlParameterSource()
                .addValue("email", email.toLowerCase(Locale.ROOT))
                .addValue("username", username)
                .addValue("passwordHash", passwordHash)
                .addValue("status", status)
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion)
                .addValue("now", dbTime(now)), Long.class);
        if (userIds.isEmpty()) {
            return 0L;
        }
        long userId = userIds.getFirst();
        jdbc.update("""
            INSERT INTO operator_profile(user_id, callsign, display_name, created_at, updated_at)
            VALUES (:userId, :callsign, :displayName, :now, :now)
            """, Map.of("userId", userId, "callsign", callsign, "displayName", displayName, "now", dbTime(now)));
        jdbc.update("""
            INSERT INTO user_role(user_id, role_id, granted_at)
            SELECT :userId, id, :now FROM role WHERE code = 'USER'
            """, Map.of("userId", userId, "now", dbTime(now)));
        return userId;
    }

    public void saveAuthToken(long userId, String purpose, String tokenHash, Instant expiresAt, Instant now) {
        jdbc.update("UPDATE auth_token SET consumed_at = :now WHERE user_id = :userId AND purpose = :purpose AND consumed_at IS NULL",
            Map.of("now", dbTime(now), "userId", userId, "purpose", purpose));
        jdbc.update("""
            INSERT INTO auth_token(user_id, purpose, token_hash, expires_at, created_at)
            VALUES (:userId, :purpose, :tokenHash, :expiresAt, :now)
            """, Map.of("userId", userId, "purpose", purpose, "tokenHash", tokenHash,
                "expiresAt", dbTime(expiresAt), "now", dbTime(now)));
    }

    public Optional<Long> consumeAuthToken(String tokenHash, String purpose, Instant now) {
        List<Long> ids = jdbc.queryForList("""
            UPDATE auth_token
            SET consumed_at = :now
            WHERE token_hash = :tokenHash AND purpose = :purpose
              AND consumed_at IS NULL AND expires_at > :now
            RETURNING user_id
            """, Map.of("tokenHash", tokenHash, "purpose", purpose, "now", dbTime(now)), Long.class);
        return ids.stream().findFirst();
    }

    public void activateUser(long userId, Instant now) {
        jdbc.update("""
            UPDATE app_user SET status = 'ACTIVE', email_verified_at = COALESCE(email_verified_at, :now), updated_at = :now
            WHERE id = :userId AND status = 'PENDING_EMAIL'
            """, Map.of("userId", userId, "now", dbTime(now)));
    }

    public void updatePasswordAndRevokeSessions(long userId, String passwordHash, Instant now) {
        jdbc.update("UPDATE app_user SET password_hash = :passwordHash, updated_at = :now WHERE id = :userId AND status = 'ACTIVE'",
            Map.of("passwordHash", passwordHash, "now", dbTime(now), "userId", userId));
        jdbc.update("UPDATE user_session SET revoked_at = :now WHERE user_id = :userId AND revoked_at IS NULL",
            Map.of("now", dbTime(now), "userId", userId));
    }

    public void saveSession(long userId, String tokenHash, String userAgentHash, String ipHash, Instant expiresAt, Instant now) {
        jdbc.update("""
            INSERT INTO user_session(user_id, token_hash, user_agent_hash, ip_prefix_hash, created_at, last_seen_at, expires_at)
            VALUES (:userId, :tokenHash, :userAgentHash, :ipHash, :now, :now, :expiresAt)
            """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("userAgentHash", userAgentHash)
                .addValue("ipHash", ipHash)
                .addValue("now", dbTime(now))
                .addValue("expiresAt", dbTime(expiresAt)));
    }

    public void revokeSession(String tokenHash, Instant now) {
        jdbc.update("UPDATE user_session SET revoked_at = :now WHERE token_hash = :tokenHash AND revoked_at IS NULL",
            Map.of("now", dbTime(now), "tokenHash", tokenHash));
    }

    public void linkOidc(long userId, String issuer, String subject, String email, Instant now) {
        jdbc.update("""
            INSERT INTO user_oidc_identity(user_id, provider, issuer, subject, email_at_link, linked_at)
            VALUES (:userId, 'google', :issuer, :subject, :email, :now)
            """, Map.of("userId", userId, "issuer", issuer, "subject", subject, "email", email,
                "now", dbTime(now)));
    }

    public void saveGoogleIntent(String tokenHash, String termsVersion, String privacyVersion, Instant now, Instant expiresAt) {
        jdbc.update("""
            INSERT INTO oauth_registration_intent(token_hash, terms_version, privacy_version, accepted_at, expires_at)
            VALUES (:tokenHash, :termsVersion, :privacyVersion, :now, :expiresAt)
            """, Map.of("tokenHash", tokenHash, "termsVersion", termsVersion, "privacyVersion", privacyVersion,
                "now", dbTime(now), "expiresAt", dbTime(expiresAt)));
    }

    public int deleteStaleGoogleIntents(Instant now) {
        return jdbc.update("""
            DELETE FROM oauth_registration_intent
            WHERE expires_at <= :now OR consumed_at IS NOT NULL
            """, Map.of("now", dbTime(now)));
    }

    public boolean consumeGoogleIntent(String tokenHash, Instant now) {
        return jdbc.update("""
            UPDATE oauth_registration_intent SET consumed_at = :now
            WHERE token_hash = :tokenHash AND consumed_at IS NULL AND expires_at > :now
            """, Map.of("tokenHash", tokenHash, "now", dbTime(now))) == 1;
    }

    public void recordAudit(Long actorUserId, String action, String entityType, UUID entityPublicId,
                            String correlationId, String sessionHash, String ipHash, Instant now) {
        jdbc.update("""
            INSERT INTO audit_event(actor_user_id, action, entity_type, entity_public_id,
                                    correlation_id, session_hash, ip_prefix_hash, occurred_at)
            VALUES (:actorUserId, :action, :entityType, :entityPublicId,
                    :correlationId, :sessionHash, :ipHash, :now)
            """, new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("action", action)
                .addValue("entityType", entityType)
                .addValue("entityPublicId", entityPublicId)
                .addValue("correlationId", correlationId)
                .addValue("sessionHash", sessionHash)
                .addValue("ipHash", ipHash)
                .addValue("now", dbTime(now)));
    }

    private static Timestamp dbTime(Instant value) {
        return Timestamp.from(value);
    }
}
