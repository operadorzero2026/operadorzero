package br.com.operadorzero.operator;

import br.com.operadorzero.operator.OperatorDtos.EquipmentResponse;
import br.com.operadorzero.operator.OperatorDtos.OperatorSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperatorRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OperatorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProfileRow> findProfile(long userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT p.id AS profile_id, p.public_id, u.email, u.username, p.display_name, p.callsign,
                       p.bio, p.city, p.state_code, p.preferred_position, p.recruitment_status,
                       p.airsoft_started_at, p.version
                FROM operator_profile p
                JOIN app_user u ON u.id = p.user_id
                WHERE p.user_id = :userId AND u.status = 'ACTIVE'
                """, Map.of("userId", userId), this::mapProfile));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<String> secondaryPositions(long profileId) {
        return jdbc.queryForList("""
            SELECT position_code FROM operator_position_preference
            WHERE operator_profile_id = :profileId ORDER BY preference_order
            """, Map.of("profileId", profileId), String.class);
    }

    public Map<String, String> privacy(long profileId) {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query("""
            SELECT field_code, visibility FROM operator_privacy_setting
            WHERE operator_profile_id = :profileId ORDER BY field_code
            """, Map.of("profileId", profileId), (row, index) -> Map.entry(
                row.getString("field_code"), row.getString("visibility")))
            .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(result);
    }

    public List<EquipmentResponse> equipment(long profileId) {
        return jdbc.query("""
            SELECT public_id, category, name, details, item_condition, visibility, created_at
            FROM operator_equipment WHERE operator_profile_id = :profileId
            ORDER BY created_at DESC
            """, Map.of("profileId", profileId), (row, index) -> new EquipmentResponse(
                row.getObject("public_id", UUID.class), row.getString("category"), row.getString("name"),
                row.getString("details"), row.getString("item_condition"), row.getString("visibility"),
                row.getTimestamp("created_at").toInstant()));
    }

    public boolean usernameExistsForOther(String username, long userId) {
        Boolean exists = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM app_user WHERE lower(username) = lower(:username) AND id <> :userId)
            """, Map.of("username", username, "userId", userId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<Instant> lastUsernameChange(long userId) {
        List<Timestamp> values = jdbc.queryForList("""
            SELECT changed_at FROM operator_username_history WHERE user_id = :userId
            ORDER BY changed_at DESC LIMIT 1
            """, Map.of("userId", userId), Timestamp.class);
        return values.stream().findFirst().map(Timestamp::toInstant);
    }

    public int updateProfile(long userId, long expectedVersion, String displayName, String callsign, String bio,
                             String city, String stateCode, String preferredPosition, String recruitmentStatus,
                             LocalDate airsoftStartedAt, Instant now) {
        return jdbc.update("""
            UPDATE operator_profile
            SET display_name = :displayName, callsign = :callsign, bio = :bio, city = :city,
                state_code = :stateCode, preferred_position = :preferredPosition,
                recruitment_status = :recruitmentStatus, airsoft_started_at = :airsoftStartedAt,
                updated_at = :now, version = version + 1
            WHERE user_id = :userId AND version = :expectedVersion
            """, new MapSqlParameterSource()
                .addValue("displayName", displayName).addValue("callsign", callsign).addValue("bio", bio)
                .addValue("city", city).addValue("stateCode", stateCode).addValue("preferredPosition", preferredPosition)
                .addValue("recruitmentStatus", recruitmentStatus).addValue("airsoftStartedAt", airsoftStartedAt)
                .addValue("now", Timestamp.from(now))
                .addValue("userId", userId).addValue("expectedVersion", expectedVersion));
    }

    public void updateUsername(long userId, String previous, String next, Instant now) {
        jdbc.update("UPDATE app_user SET username = :username, updated_at = :now, version = version + 1 WHERE id = :userId",
            Map.of("username", next, "now", Timestamp.from(now), "userId", userId));
        jdbc.update("""
            INSERT INTO operator_username_history(user_id, previous_username, new_username, changed_at)
            VALUES (:userId, :previous, :next, :now)
            """, Map.of("userId", userId, "previous", previous, "next", next, "now", Timestamp.from(now)));
    }

    public void replaceSecondaryPositions(long profileId, List<String> positions, Instant now) {
        jdbc.update("DELETE FROM operator_position_preference WHERE operator_profile_id = :profileId", Map.of("profileId", profileId));
        for (int index = 0; index < positions.size(); index++) {
            jdbc.update("""
                INSERT INTO operator_position_preference(operator_profile_id, position_code, preference_order, created_at)
                VALUES (:profileId, :position, :positionOrder, :now)
                """, Map.of("profileId", profileId, "position", positions.get(index), "positionOrder", index + 1,
                    "now", Timestamp.from(now)));
        }
    }

    public void replacePrivacy(long profileId, Map<String, String> fields, Instant now) {
        fields.forEach((field, visibility) -> jdbc.update("""
            INSERT INTO operator_privacy_setting(operator_profile_id, field_code, visibility, updated_at)
            VALUES (:profileId, :field, :visibility, :now)
            ON CONFLICT (operator_profile_id, field_code)
            DO UPDATE SET visibility = EXCLUDED.visibility, updated_at = EXCLUDED.updated_at
            """, Map.of("profileId", profileId, "field", field, "visibility", visibility, "now", Timestamp.from(now))));
    }

    public int equipmentCount(long profileId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM operator_equipment WHERE operator_profile_id = :profileId",
            Map.of("profileId", profileId), Integer.class);
        return count == null ? 0 : count;
    }

    public UUID createEquipment(long profileId, String category, String name, String details, String condition,
                                String visibility, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operator_equipment(operator_profile_id, category, name, details, item_condition, visibility, created_at, updated_at)
            VALUES (:profileId, :category, :name, :details, :condition, :visibility, :now, :now)
            RETURNING public_id
            """, new MapSqlParameterSource().addValue("profileId", profileId).addValue("category", category)
                .addValue("name", name).addValue("details", details).addValue("condition", condition)
                .addValue("visibility", visibility).addValue("now", Timestamp.from(now)), UUID.class);
    }

    public int deleteEquipment(long userId, UUID equipmentId) {
        return jdbc.update("""
            DELETE FROM operator_equipment e USING operator_profile p
            WHERE e.operator_profile_id = p.id AND p.user_id = :userId AND e.public_id = :equipmentId
            """, Map.of("userId", userId, "equipmentId", equipmentId));
    }

    public void savePhoto(long profileId, String contentType, byte[] imageData, Instant now) {
        jdbc.update("""
            INSERT INTO operator_profile_photo(operator_profile_id, content_type, image_data, updated_at)
            VALUES (:profileId, :contentType, :imageData, :now)
            ON CONFLICT (operator_profile_id) DO UPDATE
            SET content_type = EXCLUDED.content_type, image_data = EXCLUDED.image_data, updated_at = EXCLUDED.updated_at
            """, new MapSqlParameterSource().addValue("profileId", profileId).addValue("contentType", contentType)
            .addValue("imageData", imageData).addValue("now", Timestamp.from(now)));
        jdbc.update("UPDATE operator_profile SET version = version + 1, updated_at = :now WHERE id = :profileId",
            Map.of("profileId", profileId, "now", Timestamp.from(now)));
    }

    public boolean hasPhoto(long profileId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM operator_profile_photo WHERE operator_profile_id = :profileId)",
            Map.of("profileId", profileId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<PhotoRow> findPhoto(long userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT f.content_type, f.image_data FROM operator_profile_photo f
                JOIN operator_profile p ON p.id = f.operator_profile_id
                JOIN app_user u ON u.id = p.user_id
                WHERE p.user_id = :userId AND u.status = 'ACTIVE'
                """, Map.of("userId", userId), (row, index) -> new PhotoRow(
                    row.getString("content_type"), row.getBytes("image_data"))));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<OperatorSummary> search(long viewerUserId, String query, int limit) {
        String escaped = query.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.query("""
            SELECT p.public_id, u.username, p.display_name, p.callsign,
                   CASE WHEN loc.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.city END AS city,
                   CASE WHEN loc.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.state_code END AS state_code,
                    CASE WHEN team_status.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.recruitment_status END AS recruitment_status,
                   t.name AS team_name, t.acronym AS team_acronym, p.airsoft_started_at
            FROM operator_profile p
            JOIN app_user u ON u.id = p.user_id
            LEFT JOIN team_member tm ON tm.user_id = u.id AND tm.left_at IS NULL
            LEFT JOIN team t ON t.id = tm.team_id AND t.status = 'ACTIVE'
            LEFT JOIN operator_privacy_setting loc ON loc.operator_profile_id = p.id AND loc.field_code = 'LOCATION'
            LEFT JOIN operator_privacy_setting team_status ON team_status.operator_profile_id = p.id AND team_status.field_code = 'TEAM_STATUS'
            WHERE u.status = 'ACTIVE' AND u.id <> :viewerUserId
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id=:viewerUserId AND ob.blocked_user_id=u.id)
                     OR (ob.blocker_user_id=u.id AND ob.blocked_user_id=:viewerUserId))
              AND (lower(u.username) LIKE :query ESCAPE '\\'
                OR lower(p.callsign) LIKE :query ESCAPE '\\'
                OR lower(p.display_name) LIKE :query ESCAPE '\\')
            ORDER BY CASE WHEN lower(u.username) = :exact OR lower(p.callsign) = :exact THEN 0 ELSE 1 END,
                     lower(p.callsign), p.public_id
            LIMIT :limit
            """, new MapSqlParameterSource().addValue("viewerUserId", viewerUserId)
                .addValue("query", "%" + escaped + "%").addValue("exact", query.toLowerCase()).addValue("limit", limit),
            this::mapSummary);
    }

    public Optional<OperatorSummary> findPublicProfile(long viewerUserId, String username) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT p.public_id, u.username, p.display_name, p.callsign,
                       CASE WHEN u.id = :viewerUserId OR loc.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.city END AS city,
                       CASE WHEN u.id = :viewerUserId OR loc.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.state_code END AS state_code,
                        CASE WHEN u.id = :viewerUserId OR team_status.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.recruitment_status END AS recruitment_status,
                       t.name AS team_name, t.acronym AS team_acronym, p.airsoft_started_at
                FROM operator_profile p
                JOIN app_user u ON u.id = p.user_id
                LEFT JOIN team_member tm ON tm.user_id = u.id AND tm.left_at IS NULL
                LEFT JOIN team t ON t.id = tm.team_id AND t.status = 'ACTIVE'
                LEFT JOIN operator_privacy_setting loc ON loc.operator_profile_id = p.id AND loc.field_code = 'LOCATION'
                LEFT JOIN operator_privacy_setting team_status ON team_status.operator_profile_id = p.id AND team_status.field_code = 'TEAM_STATUS'
                WHERE lower(u.username) = lower(:username) AND u.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM operator_block ob
                      WHERE (ob.blocker_user_id=:viewerUserId AND ob.blocked_user_id=u.id)
                         OR (ob.blocker_user_id=u.id AND ob.blocked_user_id=:viewerUserId))
                """, Map.of("viewerUserId", viewerUserId, "username", username), this::mapSummary));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ProfileRow mapProfile(ResultSet row, int index) throws SQLException {
        return new ProfileRow(row.getLong("profile_id"), row.getObject("public_id", UUID.class), row.getString("email"),
            row.getString("username"), row.getString("display_name"), row.getString("callsign"), row.getString("bio"),
            row.getString("city"), row.getString("state_code"), row.getString("preferred_position"),
            row.getString("recruitment_status"), row.getObject("airsoft_started_at", LocalDate.class), row.getLong("version"));
    }

    private OperatorSummary mapSummary(ResultSet row, int index) throws SQLException {
        return new OperatorSummary(row.getObject("public_id", UUID.class), row.getString("username"),
            row.getString("display_name"), row.getString("callsign"), row.getString("city"),
            row.getString("state_code"), row.getString("recruitment_status"), row.getString("team_name"),
            row.getString("team_acronym"),
            row.getObject("airsoft_started_at", LocalDate.class), null);
    }

    public record ProfileRow(long profileId, UUID publicId, String email, String username, String displayName,
                             String callsign, String bio, String city, String stateCode, String preferredPosition,
                             String recruitmentStatus, LocalDate airsoftStartedAt, long version) {}
    public record PhotoRow(String contentType, byte[] data) {}
}
