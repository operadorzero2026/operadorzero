package br.com.operadorzero.operator;

import br.com.operadorzero.operator.OperatorSocialDtos.ConnectionResponse;
import br.com.operadorzero.operator.OperatorSocialDtos.ProfileEquipmentResponse;
import br.com.operadorzero.operator.OperatorSocialDtos.ProfileOperationResponse;
import br.com.operadorzero.operator.OperatorSocialDtos.ProfileOverviewResponse;
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
public class OperatorSocialRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OperatorSocialRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Long> userId(String username) {
        try { return Optional.ofNullable(jdbc.queryForObject("SELECT id FROM app_user WHERE lower(username)=lower(:username) AND status='ACTIVE'", Map.of("username", username), Long.class)); }
        catch (EmptyResultDataAccessException ignored) { return Optional.empty(); }
    }

    public Optional<ProfileOverviewResponse> profile(long viewerUserId, String username) {
        try {
            ProfileBase base = jdbc.queryForObject("""
                SELECT u.id user_id,p.public_id,u.username,p.display_name,p.callsign,
                  CASE WHEN u.id=:viewer OR loc.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.city END city,
                  CASE WHEN u.id=:viewer OR loc.visibility IN ('AUTHENTICATED','PUBLIC') THEN p.state_code END state_code,
                  p.recruitment_status,t.name team_name,t.acronym team_acronym,p.airsoft_started_at,
                  p.bio,p.preferred_position,tm.role team_role,(u.id=:viewer) own_profile,
                  ((u.id=:viewer) AND photo.operator_profile_id IS NOT NULL) has_photo,
                  COALESCE(EXTRACT(EPOCH FROM photo.updated_at)::bigint,0) photo_version,
                  (SELECT count(*) FROM community_post cp WHERE cp.author_user_id=u.id AND cp.status='PUBLISHED') publication_count,
                  (SELECT count(*) FROM operator_friendship f WHERE f.status='ACCEPTED' AND (f.requester_user_id=u.id OR f.addressee_user_id=u.id)) friend_count,
                  (SELECT count(*) FROM operation_participant part WHERE part.user_id=u.id AND part.status IN ('APPROVED','CONFIRMED','CHECKED_IN')) operation_count
                FROM app_user u JOIN operator_profile p ON p.user_id=u.id
                LEFT JOIN operator_privacy_setting loc ON loc.operator_profile_id=p.id AND loc.field_code='LOCATION'
                LEFT JOIN team_member tm ON tm.user_id=u.id AND tm.left_at IS NULL
                LEFT JOIN team t ON t.id=tm.team_id AND t.status='ACTIVE'
                LEFT JOIN operator_profile_photo photo ON photo.operator_profile_id=p.id
                WHERE lower(u.username)=lower(:username) AND u.status='ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM operator_block b WHERE (b.blocker_user_id=:viewer AND b.blocked_user_id=u.id) OR (b.blocker_user_id=u.id AND b.blocked_user_id=:viewer))
                """, Map.of("viewer", viewerUserId, "username", username), (r,i) -> new ProfileBase(
                    r.getLong("user_id"), new OperatorDtos.OperatorSummary(r.getObject("public_id",UUID.class),r.getString("username"),r.getString("display_name"),r.getString("callsign"),r.getString("city"),r.getString("state_code"),r.getString("recruitment_status"),r.getString("team_name"),r.getString("team_acronym"),r.getObject("airsoft_started_at",java.time.LocalDate.class),null),
                    r.getString("bio"),r.getString("preferred_position"),r.getString("team_role"),r.getBoolean("own_profile"),r.getBoolean("has_photo"),r.getLong("photo_version"),r.getLong("publication_count"),r.getLong("friend_count"),r.getLong("operation_count")));
            if (base == null) return Optional.empty();
            var equipment = jdbc.query("""
                SELECT e.category,e.name FROM operator_equipment e JOIN operator_profile p ON p.id=e.operator_profile_id
                WHERE p.user_id=:target AND (:viewer=:target OR e.visibility IN ('AUTHENTICATED','PUBLIC'))
                ORDER BY e.created_at DESC LIMIT 5
                """, Map.of("viewer",viewerUserId,"target",base.userId()), (r,i)->new ProfileEquipmentResponse(r.getString("category"),r.getString("name")));
            var operations = jdbc.query("""
                SELECT o.public_id,o.name,o.operation_date,o.city,o.state_code,o.status,(cover.operation_id IS NOT NULL) has_cover,COALESCE(cover.version,0) cover_version
                FROM operation_participant part JOIN airsoft_operation o ON o.id=part.operation_id
                LEFT JOIN operation_cover cover ON cover.operation_id=o.id
                WHERE part.user_id=:target AND part.status IN ('APPROVED','CONFIRMED','CHECKED_IN')
                ORDER BY o.operation_date DESC,o.id DESC LIMIT 5
                """, Map.of("target",base.userId()), (r,i)->new ProfileOperationResponse(r.getObject("public_id",UUID.class),r.getString("name"),r.getObject("operation_date",java.time.LocalDate.class),r.getString("city"),r.getString("state_code"),r.getString("status"),r.getBoolean("has_cover"),r.getLong("cover_version")));
            var mutual = viewerUserId == base.userId() ? List.<OperatorDtos.OperatorSummary>of() : jdbc.query("""
                SELECT p.public_id,u.username,p.display_name,p.callsign,NULL city,NULL state_code,p.recruitment_status,t.name team_name,t.acronym team_acronym,p.airsoft_started_at
                FROM app_user u JOIN operator_profile p ON p.user_id=u.id
                LEFT JOIN team_member tm ON tm.user_id=u.id AND tm.left_at IS NULL LEFT JOIN team t ON t.id=tm.team_id
                WHERE EXISTS(SELECT 1 FROM operator_friendship a WHERE a.status='ACCEPTED' AND ((a.requester_user_id=:viewer AND a.addressee_user_id=u.id) OR (a.addressee_user_id=:viewer AND a.requester_user_id=u.id)))
                  AND EXISTS(SELECT 1 FROM operator_friendship b WHERE b.status='ACCEPTED' AND ((b.requester_user_id=:target AND b.addressee_user_id=u.id) OR (b.addressee_user_id=:target AND b.requester_user_id=u.id)))
                LIMIT 6
                """, Map.of("viewer",viewerUserId,"target",base.userId()), (r,i)->new OperatorDtos.OperatorSummary(r.getObject("public_id",UUID.class),r.getString("username"),r.getString("display_name"),r.getString("callsign"),null,null,r.getString("recruitment_status"),r.getString("team_name"),r.getString("team_acronym"),r.getObject("airsoft_started_at",java.time.LocalDate.class),null));
            return Optional.of(new ProfileOverviewResponse(base.operator(),base.bio(),base.preferredPosition(),base.teamRole(),base.ownProfile(),base.hasPhoto(),base.photoVersion(),base.publicationCount(),base.friendCount(),base.operationCount(),null,0,equipment,operations,mutual));
        } catch (EmptyResultDataAccessException ignored) { return Optional.empty(); }
    }

    public boolean blocked(long first, long second) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM operator_block WHERE (blocker_user_id=:a AND blocked_user_id=:b) OR (blocker_user_id=:b AND blocked_user_id=:a))", Map.of("a", first, "b", second), Boolean.class));
    }

    public Optional<ConnectionRow> activeConnection(long first, long second) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
            SELECT id, public_id, requester_user_id, addressee_user_id, status
            FROM operator_friendship
            WHERE ((requester_user_id=:a AND addressee_user_id=:b) OR (requester_user_id=:b AND addressee_user_id=:a))
              AND status IN ('PENDING','ACCEPTED') ORDER BY id DESC LIMIT 1
            """, Map.of("a", first, "b", second), (row, index) -> new ConnectionRow(row.getLong("id"), row.getObject("public_id", UUID.class), row.getLong("requester_user_id"), row.getLong("addressee_user_id"), row.getString("status")))); }
        catch (EmptyResultDataAccessException ignored) { return Optional.empty(); }
    }

    public UUID create(long requester, long addressee, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operator_friendship(requester_user_id,addressee_user_id,status,created_at,updated_at)
            VALUES (:requester,:addressee,'PENDING',:now,:now) RETURNING public_id
            """, new MapSqlParameterSource().addValue("requester", requester).addValue("addressee", addressee).addValue("now", Timestamp.from(now)), UUID.class);
    }

    public int transition(UUID id, long userId, String from, String to, boolean recipientOnly, Instant now) {
        String ownership = recipientOnly ? "addressee_user_id=:userId" : "(requester_user_id=:userId OR addressee_user_id=:userId)";
        return jdbc.update("UPDATE operator_friendship SET status=:to,updated_at=:now WHERE public_id=:id AND status=:from AND " + ownership,
            new MapSqlParameterSource().addValue("id", id).addValue("userId", userId).addValue("from", from).addValue("to", to).addValue("now", Timestamp.from(now)));
    }

    public int cancel(UUID id, long requesterUserId, Instant now) {
        return jdbc.update("UPDATE operator_friendship SET status='CANCELLED',updated_at=:now WHERE public_id=:id AND status='PENDING' AND requester_user_id=:userId",
            new MapSqlParameterSource().addValue("id", id).addValue("userId", requesterUserId).addValue("now", Timestamp.from(now)));
    }

    public List<ConnectionResponse> connections(long userId, String status) {
        return jdbc.query("""
            SELECT f.public_id connection_id,f.status,f.updated_at,f.addressee_user_id=:userId received,
                   p.public_id,u.username,p.display_name,p.callsign,p.city,p.state_code,p.recruitment_status,
                   t.name team_name,t.acronym team_acronym,p.airsoft_started_at
            FROM operator_friendship f
            JOIN app_user u ON u.id=CASE WHEN f.requester_user_id=:userId THEN f.addressee_user_id ELSE f.requester_user_id END
            JOIN operator_profile p ON p.user_id=u.id
            LEFT JOIN team_member tm ON tm.user_id=u.id AND tm.left_at IS NULL
            LEFT JOIN team t ON t.id=tm.team_id AND t.status='ACTIVE'
            WHERE (f.requester_user_id=:userId OR f.addressee_user_id=:userId) AND f.status=:status
              AND NOT EXISTS(SELECT 1 FROM operator_block b WHERE (b.blocker_user_id=:userId AND b.blocked_user_id=u.id) OR (b.blocker_user_id=u.id AND b.blocked_user_id=:userId))
            ORDER BY f.updated_at DESC,f.id DESC LIMIT 100
            """, Map.of("userId", userId, "status", status), this::mapConnection);
    }

    public void block(long blocker, long blocked, Instant now) {
        jdbc.update("INSERT INTO operator_block(blocker_user_id,blocked_user_id,created_at) VALUES (:a,:b,:now) ON CONFLICT DO NOTHING", Map.of("a", blocker,"b",blocked,"now",Timestamp.from(now)));
        jdbc.update("UPDATE operator_friendship SET status='REMOVED',updated_at=:now WHERE ((requester_user_id=:a AND addressee_user_id=:b) OR (requester_user_id=:b AND addressee_user_id=:a)) AND status IN ('PENDING','ACCEPTED')", Map.of("a",blocker,"b",blocked,"now",Timestamp.from(now)));
    }
    public int unblock(long blocker, long blocked) { return jdbc.update("DELETE FROM operator_block WHERE blocker_user_id=:a AND blocked_user_id=:b", Map.of("a",blocker,"b",blocked)); }
    public List<OperatorDtos.OperatorSummary> blockedOperators(long blocker) {
        return jdbc.query("""
            SELECT p.public_id,u.username,p.display_name,p.callsign,p.city,p.state_code,p.recruitment_status,
                   t.name team_name,t.acronym team_acronym,p.airsoft_started_at
            FROM operator_block b JOIN app_user u ON u.id=b.blocked_user_id AND u.status='ACTIVE'
            JOIN operator_profile p ON p.user_id=u.id
            LEFT JOIN team_member tm ON tm.user_id=u.id AND tm.left_at IS NULL
            LEFT JOIN team t ON t.id=tm.team_id AND t.status='ACTIVE'
            WHERE b.blocker_user_id=:blocker ORDER BY b.created_at DESC
            """, Map.of("blocker",blocker), (row,index) -> new OperatorDtos.OperatorSummary(
                row.getObject("public_id",UUID.class),row.getString("username"),row.getString("display_name"),row.getString("callsign"),row.getString("city"),row.getString("state_code"),row.getString("recruitment_status"),row.getString("team_name"),row.getString("team_acronym"),row.getObject("airsoft_started_at",java.time.LocalDate.class),null));
    }
    public void report(long reporter,long reported,String reason,String details,Instant now) { jdbc.update("INSERT INTO operator_profile_report(reporter_user_id,reported_user_id,reason,details,created_at) VALUES (:a,:b,:reason,:details,:now)", Map.of("a",reporter,"b",reported,"reason",reason,"details",details == null ? "" : details.trim(),"now",Timestamp.from(now))); }

    private ConnectionResponse mapConnection(ResultSet row,int index) throws SQLException {
        var operator = new OperatorDtos.OperatorSummary(row.getObject("public_id",UUID.class),row.getString("username"),row.getString("display_name"),row.getString("callsign"),row.getString("city"),row.getString("state_code"),row.getString("recruitment_status"),row.getString("team_name"),row.getString("team_acronym"),row.getObject("airsoft_started_at",java.time.LocalDate.class),null);
        return new ConnectionResponse(row.getObject("connection_id",UUID.class),operator,row.getString("status"),row.getBoolean("received"),row.getTimestamp("updated_at").toInstant());
    }
    public record ConnectionRow(long id,UUID publicId,long requester,long addressee,String status) {}
    private record ProfileBase(long userId,OperatorDtos.OperatorSummary operator,String bio,String preferredPosition,String teamRole,boolean ownProfile,boolean hasPhoto,long photoVersion,long publicationCount,long friendCount,long operationCount) {}
}
