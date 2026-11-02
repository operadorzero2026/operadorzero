package br.com.operadorzero.team;

import br.com.operadorzero.team.TeamDtos.InvitationResponse;
import br.com.operadorzero.team.TeamDtos.JoinRequestResponse;
import br.com.operadorzero.team.TeamDtos.TeamCardResponse;
import br.com.operadorzero.team.TeamDtos.MemberResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeamRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public TeamRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countActiveTeams() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE status = 'ACTIVE'", Map.of(), Long.class);
        return total == null ? 0 : total;
    }

    public Optional<MembershipRow> activeMembership(long userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT tm.team_id, tm.user_id, tm.role, t.public_id AS team_public_id
                FROM team_member tm JOIN team t ON t.id = tm.team_id
                WHERE tm.user_id = :userId AND tm.left_at IS NULL AND t.status = 'ACTIVE'
                """, Map.of("userId", userId), this::mapMembership));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<MembershipRow> activeMembership(long userId, UUID teamId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT tm.team_id, tm.user_id, tm.role, t.public_id AS team_public_id
                FROM team_member tm JOIN team t ON t.id = tm.team_id
                WHERE tm.user_id = :userId AND tm.left_at IS NULL AND t.public_id = :teamId AND t.status = 'ACTIVE'
                """, Map.of("userId", userId, "teamId", teamId), this::mapMembership));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<TeamRow> findTeam(long internalId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT id, public_id, name, acronym, city, state_code, game_style, owns_field,
                       description, recruitment_status, version
                FROM team WHERE id = :id AND status = 'ACTIVE'
                """, Map.of("id", internalId), this::mapTeam));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void saveLogo(long teamId, long userId, String contentType, byte[] imageData, Instant now) {
        jdbc.update("""
            INSERT INTO team_logo(team_id, content_type, image_data, updated_by, updated_at)
            VALUES (:teamId, :contentType, :imageData, :userId, :now)
            ON CONFLICT (team_id) DO UPDATE SET content_type = EXCLUDED.content_type,
                image_data = EXCLUDED.image_data, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
            """, new MapSqlParameterSource().addValue("teamId", teamId).addValue("contentType", contentType)
                .addValue("imageData", imageData).addValue("userId", userId).addValue("now", Timestamp.from(now)));
        jdbc.update("UPDATE team SET version = version + 1, updated_at = :now WHERE id = :teamId",
            Map.of("teamId", teamId, "now", Timestamp.from(now)));
    }

    public Optional<LogoRow> findLogo(UUID teamId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT tl.content_type, tl.image_data FROM team_logo tl
                JOIN team t ON t.id = tl.team_id
                WHERE t.public_id = :teamId AND t.status = 'ACTIVE'
                """, Map.of("teamId", teamId), (row, index) ->
                new LogoRow(row.getString("content_type"), row.getBytes("image_data"))));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public boolean hasLogo(long teamId) {
        Boolean present = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM team_logo WHERE team_id = :teamId)",
            Map.of("teamId", teamId), Boolean.class);
        return Boolean.TRUE.equals(present);
    }

    public long createTeam(long userId, String name, String acronym, String city, String stateCode,
                           String gameStyle, boolean ownsField, String description, String recruitmentStatus, Instant now) {
        Long id = jdbc.queryForObject("""
            INSERT INTO team(name, acronym, city, state_code, game_style, owns_field, description,
                             recruitment_status, created_by, created_at, updated_at)
            VALUES (:name, :acronym, :city, :stateCode, :gameStyle, :ownsField, :description,
                    :recruitmentStatus, :userId, :now, :now)
            RETURNING id
            """, new MapSqlParameterSource().addValue("name", name).addValue("acronym", acronym)
                .addValue("city", city).addValue("stateCode", stateCode).addValue("gameStyle", gameStyle)
                .addValue("ownsField", ownsField).addValue("description", description)
                .addValue("recruitmentStatus", recruitmentStatus).addValue("userId", userId)
                .addValue("now", Timestamp.from(now)), Long.class);
        return id == null ? 0L : id;
    }

    public void addMember(long teamId, long userId, String role, Long invitedBy, Instant now) {
        jdbc.update("""
            INSERT INTO team_member(team_id, user_id, role, joined_at, invited_by)
            VALUES (:teamId, :userId, :role, :now, :invitedBy)
            """, new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId)
                .addValue("role", role).addValue("now", Timestamp.from(now)).addValue("invitedBy", invitedBy));
    }

    public int updateTeam(long teamId, long expectedVersion, String name, String acronym, String city, String stateCode,
                          String gameStyle, boolean ownsField, String description, String recruitmentStatus, Instant now) {
        return jdbc.update("""
            UPDATE team SET name = :name, acronym = :acronym, city = :city, state_code = :stateCode,
                game_style = :gameStyle, owns_field = :ownsField, description = :description,
                recruitment_status = :recruitmentStatus, updated_at = :now, version = version + 1
            WHERE id = :teamId AND status = 'ACTIVE' AND version = :expectedVersion
            """, new MapSqlParameterSource().addValue("name", name).addValue("acronym", acronym)
                .addValue("city", city).addValue("stateCode", stateCode).addValue("gameStyle", gameStyle)
                .addValue("ownsField", ownsField).addValue("description", description)
                .addValue("recruitmentStatus", recruitmentStatus).addValue("now", Timestamp.from(now))
                .addValue("teamId", teamId).addValue("expectedVersion", expectedVersion));
    }

    public List<MemberResponse> members(long teamId) {
        return jdbc.query("""
            SELECT p.public_id AS operator_id, u.username, p.callsign, p.display_name, tm.role, tm.joined_at
            FROM team_member tm
            JOIN app_user u ON u.id = tm.user_id
            JOIN operator_profile p ON p.user_id = u.id
            WHERE tm.team_id = :teamId AND tm.left_at IS NULL
            ORDER BY CASE tm.role WHEN 'CAPTAIN' THEN 0 WHEN 'MANAGER' THEN 1 ELSE 2 END, lower(p.callsign)
            """, Map.of("teamId", teamId), (row, index) -> new MemberResponse(
                row.getObject("operator_id", UUID.class), row.getString("username"), row.getString("callsign"),
                row.getString("display_name"), row.getString("role"), row.getTimestamp("joined_at").toInstant()));
    }

    public Optional<UserRow> findActiveUser(UUID operatorId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT u.id, p.public_id, p.callsign FROM app_user u
                JOIN operator_profile p ON p.user_id = u.id
                WHERE p.public_id = :operatorId AND u.status = 'ACTIVE'
                """, Map.of("operatorId", operatorId), (row, index) ->
                new UserRow(row.getLong("id"), row.getObject("public_id", UUID.class), row.getString("callsign"))));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public UUID createInvitation(long teamId, long inviteeUserId, long inviterUserId, String proposedRole,
                                 String message, Instant now, Instant expiresAt) {
        return jdbc.queryForObject("""
            INSERT INTO team_invitation(team_id, invitee_user_id, inviter_user_id, proposed_role, message, created_at, expires_at)
            VALUES (:teamId, :inviteeUserId, :inviterUserId, :proposedRole, :message, :now, :expiresAt)
            RETURNING public_id
            """, new MapSqlParameterSource().addValue("teamId", teamId).addValue("inviteeUserId", inviteeUserId)
                .addValue("inviterUserId", inviterUserId).addValue("proposedRole", proposedRole)
                .addValue("message", message).addValue("now", Timestamp.from(now))
                .addValue("expiresAt", Timestamp.from(expiresAt)), UUID.class);
    }

    public List<InvitationResponse> receivedInvitations(long userId, Instant now) {
        return jdbc.query("""
            SELECT ti.public_id, t.public_id AS team_public_id, t.name, t.acronym,
                   inviter_profile.callsign AS inviter_callsign, ti.proposed_role, ti.message,
                   ti.created_at, ti.expires_at
            FROM team_invitation ti
            JOIN team t ON t.id = ti.team_id AND t.status = 'ACTIVE'
            JOIN operator_profile inviter_profile ON inviter_profile.user_id = ti.inviter_user_id
            WHERE ti.invitee_user_id = :userId AND ti.status = 'PENDING' AND ti.expires_at > :now
            ORDER BY ti.created_at DESC
            """, Map.of("userId", userId, "now", Timestamp.from(now)), (row, index) -> new InvitationResponse(
                row.getObject("public_id", UUID.class), row.getObject("team_public_id", UUID.class),
                row.getString("name"), row.getString("acronym"), row.getString("inviter_callsign"),
                row.getString("proposed_role"), row.getString("message"),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("expires_at").toInstant()));
    }

    public Optional<InvitationRow> lockInvitation(UUID invitationId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT ti.id, ti.public_id, ti.team_id, ti.invitee_user_id, ti.inviter_user_id,
                       ti.proposed_role, ti.status, ti.expires_at, t.public_id AS team_public_id
                FROM team_invitation ti JOIN team t ON t.id = ti.team_id
                WHERE ti.public_id = :invitationId FOR UPDATE
                """, Map.of("invitationId", invitationId), (row, index) -> new InvitationRow(
                row.getLong("id"), row.getObject("public_id", UUID.class), row.getLong("team_id"),
                row.getLong("invitee_user_id"), row.getLong("inviter_user_id"), row.getString("proposed_role"),
                row.getString("status"), row.getTimestamp("expires_at").toInstant(),
                row.getObject("team_public_id", UUID.class))));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void respondInvitation(long invitationId, String status, Instant now) {
        jdbc.update("UPDATE team_invitation SET status = :status, responded_at = :now WHERE id = :id",
            Map.of("status", status, "now", Timestamp.from(now), "id", invitationId));
    }

    public void cancelOtherInvitations(long inviteeUserId, long acceptedInvitationId, Instant now) {
        jdbc.update("""
            UPDATE team_invitation SET status = 'CANCELLED', responded_at = :now
            WHERE invitee_user_id = :inviteeUserId AND id <> :acceptedInvitationId AND status = 'PENDING'
            """, Map.of("now", Timestamp.from(now), "inviteeUserId", inviteeUserId,
                "acceptedInvitationId", acceptedInvitationId));
    }

    public void leave(long teamId, long userId, Instant now) {
        jdbc.update("""
            UPDATE team_member SET left_at = :now, version = version + 1
            WHERE team_id = :teamId AND user_id = :userId AND left_at IS NULL
            """, Map.of("now", Timestamp.from(now), "teamId", teamId, "userId", userId));
    }

    public Optional<MembershipRow> memberByOperator(long teamId, UUID operatorId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT tm.team_id, tm.user_id, tm.role, t.public_id AS team_public_id
                FROM team_member tm JOIN team t ON t.id = tm.team_id
                JOIN operator_profile p ON p.user_id = tm.user_id
                WHERE tm.team_id = :teamId AND p.public_id = :operatorId AND tm.left_at IS NULL
                """, Map.of("teamId", teamId, "operatorId", operatorId), this::mapMembership));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void transferCaptaincy(long teamId, long previousCaptainId, long nextCaptainId, Instant now) {
        jdbc.update("UPDATE team_member SET role = 'MANAGER', version = version + 1 WHERE team_id = :teamId AND user_id = :userId AND left_at IS NULL",
            Map.of("teamId", teamId, "userId", previousCaptainId));
        jdbc.update("UPDATE team_member SET role = 'CAPTAIN', version = version + 1 WHERE team_id = :teamId AND user_id = :userId AND left_at IS NULL",
            Map.of("teamId", teamId, "userId", nextCaptainId));
    }

    public void history(long teamId, long userId, String action, String previousRole, String newRole,
                        long actorUserId, String reason, Instant now) {
        jdbc.update("""
            INSERT INTO team_membership_history(team_id, user_id, action, previous_role, new_role,
                                                actor_user_id, reason, occurred_at)
            VALUES (:teamId, :userId, :action, :previousRole, :newRole, :actorUserId, :reason, :now)
            """, new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId)
                .addValue("action", action).addValue("previousRole", previousRole).addValue("newRole", newRole)
                .addValue("actorUserId", actorUserId).addValue("reason", reason).addValue("now", Timestamp.from(now)));
    }

    public List<TeamCardResponse> searchTeams(String query, String stateCode, String city, int limit) {
        return jdbc.query("""
            SELECT t.id, t.public_id, t.name, t.acronym, t.city, t.state_code, t.game_style, t.recruitment_status,
                   (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = t.id AND tm.left_at IS NULL) AS member_count,
                   EXISTS(SELECT 1 FROM team_logo tl WHERE tl.team_id = t.id) AS has_logo
            FROM team t
            WHERE t.status = 'ACTIVE'
              AND (:query = '' OR lower(t.name) LIKE :pattern OR lower(t.acronym) LIKE :pattern)
              AND (:stateCode = '' OR t.state_code = :stateCode)
              AND (:city = '' OR lower(t.city) = lower(:city))
            ORDER BY CASE t.recruitment_status WHEN 'OPEN' THEN 0 WHEN 'INVITE_ONLY' THEN 1 ELSE 2 END, lower(t.name)
            LIMIT :limit
            """, new MapSqlParameterSource().addValue("query", query).addValue("pattern", "%" + query.toLowerCase() + "%")
                .addValue("stateCode", stateCode).addValue("city", city).addValue("limit", limit), this::mapCard);
    }

    public Optional<TeamCardResponse> teamCard(UUID teamId) {
        return jdbc.query("""
            SELECT t.id, t.public_id, t.name, t.acronym, t.city, t.state_code, t.game_style, t.recruitment_status,
                   (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = t.id AND tm.left_at IS NULL) AS member_count,
                   EXISTS(SELECT 1 FROM team_logo tl WHERE tl.team_id = t.id) AS has_logo
            FROM team t WHERE t.public_id = :teamId AND t.status = 'ACTIVE'
            """, Map.of("teamId", teamId), this::mapCard).stream().findFirst();
    }

    public Optional<Long> teamInternalId(UUID teamId) {
        return jdbc.query("SELECT id FROM team WHERE public_id = :teamId AND status = 'ACTIVE'", Map.of("teamId", teamId),
            (row, index) -> row.getLong("id")).stream().findFirst();
    }

    public UUID createJoinRequest(long teamId, long userId, String message, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO team_join_request(team_id, user_id, message, created_at) VALUES (:teamId, :userId, :message, :now)
            RETURNING public_id
            """, new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId)
                .addValue("message", message).addValue("now", Timestamp.from(now)), UUID.class);
    }

    public boolean hasPendingJoinRequest(long teamId, long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM team_join_request WHERE team_id = :teamId AND user_id = :userId AND status = 'PENDING')",
            Map.of("teamId", teamId, "userId", userId), Boolean.class));
    }

    public int cancelOwnJoinRequest(long teamId, long userId, Instant now) {
        return jdbc.update("""
            UPDATE team_join_request SET status = 'CANCELLED', responded_at = :now
            WHERE team_id = :teamId AND user_id = :userId AND status = 'PENDING'
            """, Map.of("teamId", teamId, "userId", userId, "now", Timestamp.from(now)));
    }

    public List<JoinRequestResponse> pendingJoinRequests(long teamId) {
        return jdbc.query("""
            SELECT r.public_id, p.public_id AS operator_id, u.username, p.callsign, p.display_name, r.message, r.created_at
            FROM team_join_request r
            JOIN app_user u ON u.id = r.user_id AND u.status = 'ACTIVE'
            JOIN operator_profile p ON p.user_id = u.id
            WHERE r.team_id = :teamId AND r.status = 'PENDING'
            ORDER BY r.created_at
            """, Map.of("teamId", teamId), (row, index) -> new JoinRequestResponse(row.getObject("public_id", UUID.class),
                row.getObject("operator_id", UUID.class), row.getString("username"), row.getString("callsign"),
                row.getString("display_name"), row.getString("message"), row.getTimestamp("created_at").toInstant()));
    }

    public Optional<JoinRequestRow> lockJoinRequest(UUID requestId) {
        return jdbc.query("""
            SELECT r.id, r.public_id, r.team_id, r.user_id, r.status, t.public_id AS team_public_id
            FROM team_join_request r JOIN team t ON t.id = r.team_id
            WHERE r.public_id = :requestId FOR UPDATE OF r
            """, Map.of("requestId", requestId), (row, index) -> new JoinRequestRow(row.getLong("id"),
                row.getObject("public_id", UUID.class), row.getLong("team_id"), row.getLong("user_id"),
                row.getString("status"), row.getObject("team_public_id", UUID.class))).stream().findFirst();
    }

    public void respondJoinRequest(long requestId, String status, long actorUserId, Instant now) {
        jdbc.update("UPDATE team_join_request SET status = :status, responded_at = :now, responded_by = :actor WHERE id = :id",
            Map.of("status", status, "now", Timestamp.from(now), "actor", actorUserId, "id", requestId));
    }

    /** Ao entrar numa equipe, outros pedidos e convites pendentes do operador deixam de valer. */
    public void cancelPendingForUser(long userId, Instant now) {
        jdbc.update("UPDATE team_join_request SET status = 'CANCELLED', responded_at = :now WHERE user_id = :userId AND status = 'PENDING'",
            Map.of("userId", userId, "now", Timestamp.from(now)));
        jdbc.update("UPDATE team_invitation SET status = 'CANCELLED', responded_at = :now WHERE invitee_user_id = :userId AND status = 'PENDING'",
            Map.of("userId", userId, "now", Timestamp.from(now)));
    }

    public int changeRole(long teamId, long userId, String role) {
        return jdbc.update("""
            UPDATE team_member SET role = :role, version = version + 1
            WHERE team_id = :teamId AND user_id = :userId AND left_at IS NULL AND role <> 'CAPTAIN'
            """, Map.of("role", role, "teamId", teamId, "userId", userId));
    }

    private TeamCardResponse mapCard(ResultSet row, int index) throws SQLException {
        return new TeamCardResponse(row.getObject("public_id", UUID.class), row.getString("name"), row.getString("acronym"),
            row.getString("city"), row.getString("state_code"), row.getString("game_style"),
            row.getString("recruitment_status"), row.getLong("member_count"), row.getBoolean("has_logo"));
    }

    private MembershipRow mapMembership(ResultSet row, int index) throws SQLException {
        return new MembershipRow(row.getLong("team_id"), row.getLong("user_id"), row.getString("role"),
            row.getObject("team_public_id", UUID.class));
    }

    private TeamRow mapTeam(ResultSet row, int index) throws SQLException {
        return new TeamRow(row.getLong("id"), row.getObject("public_id", UUID.class), row.getString("name"),
            row.getString("acronym"), row.getString("city"), row.getString("state_code"), row.getString("game_style"),
            row.getBoolean("owns_field"), row.getString("description"), row.getString("recruitment_status"),
            row.getLong("version"));
    }

    public record MembershipRow(long teamId, long userId, String role, UUID teamPublicId) {}
    public record TeamRow(long id, UUID publicId, String name, String acronym, String city, String stateCode,
                          String gameStyle, boolean ownsField, String description, String recruitmentStatus, long version) {}
    public record UserRow(long id, UUID publicId, String callsign) {}
    public record InvitationRow(long id, UUID publicId, long teamId, long inviteeUserId, long inviterUserId,
                                String proposedRole, String status, Instant expiresAt, UUID teamPublicId) {}
    public record LogoRow(String contentType, byte[] imageData) {}
    public record JoinRequestRow(long id, UUID publicId, long teamId, long userId, String status, UUID teamPublicId) {}
}

