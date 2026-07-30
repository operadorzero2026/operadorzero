package br.com.operadorzero.operation;

import static br.com.operadorzero.operation.OperationStructureDtos.*;

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
public class OperationStructureRepository {
    private static final String ACTIVE = "('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')";
    private final NamedParameterJdbcTemplate jdbc;
    public OperationStructureRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<OperationAccess> access(UUID operationId, long userId, boolean lock) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT o.id,o.public_id,o.organizer_user_id,o.status,o.game_size,o.participant_limit,o.command_roles_enabled,
                       o.allow_role_accumulation,o.organizer_user_id=:userId owner,
                       EXISTS(SELECT 1 FROM operation_role_assignment r WHERE r.operation_id=o.id AND r.user_id=:userId
                              AND r.role='OPERATION_ADMIN') operation_admin
                FROM airsoft_operation o WHERE o.public_id=:id AND o.deleted_at IS NULL
                  AND (o.status<>'DRAFT' OR o.organizer_user_id=:userId OR EXISTS(
                    SELECT 1 FROM operation_role_assignment ar WHERE ar.operation_id=o.id AND ar.user_id=:userId AND ar.role='OPERATION_ADMIN'))
                """ + (lock ? " FOR UPDATE" : ""), Map.of("id", operationId, "userId", userId), (r,i) ->
                new OperationAccess(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("organizer_user_id"),
                    r.getString("status"), r.getString("game_size"), (Integer) r.getObject("participant_limit"), r.getBoolean("command_roles_enabled"),
                    r.getBoolean("allow_role_accumulation"), r.getBoolean("owner"), r.getBoolean("operation_admin"))));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public StructureResponse structure(OperationAccess access, long userId) {
        List<RoleAssignmentResponse> roles = jdbc.query("""
            SELECT r.public_id,p.public_id operator_id,p.callsign,r.role,t.public_id team_id,s.public_id squad_id
            FROM operation_role_assignment r JOIN operator_profile p ON p.user_id=r.user_id
            LEFT JOIN operation_team t ON t.id=r.operation_team_id LEFT JOIN operation_squad s ON s.id=r.operation_squad_id
            WHERE r.operation_id=:operationId ORDER BY r.created_at
            """, Map.of("operationId", access.id()), (r,i) -> new RoleAssignmentResponse(r.getObject("public_id",UUID.class),
                r.getObject("operator_id",UUID.class),r.getString("callsign"),r.getString("role"),
                r.getObject("team_id",UUID.class),r.getObject("squad_id",UUID.class)));
        List<SquadResponse> squads = jdbc.query("""
            SELECT s.public_id,s.operation_team_id,t.public_id team_id,s.name,s.acronym,s.description,s.capacity,s.sort_order,s.status,
                   count(p.id) FILTER (WHERE p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')) participant_count,
                   cp.public_id commander_id,cp.callsign commander_callsign,rp.public_id radio_id,rp.callsign radio_callsign
            FROM operation_squad s JOIN operation_team t ON t.id=s.operation_team_id
            LEFT JOIN operation_participant p ON p.operation_squad_id=s.id
            LEFT JOIN operation_role_assignment cr ON cr.operation_squad_id=s.id AND cr.role='SQUAD_COMMANDER'
            LEFT JOIN operator_profile cp ON cp.user_id=cr.user_id
            LEFT JOIN operation_role_assignment rr ON rr.operation_squad_id=s.id AND rr.role='SQUAD_RADIO'
            LEFT JOIN operator_profile rp ON rp.user_id=rr.user_id
            WHERE t.operation_id=:operationId GROUP BY s.id,t.public_id,t.sort_order,cp.public_id,cp.callsign,rp.public_id,rp.callsign
            ORDER BY t.sort_order,s.sort_order
            """, Map.of("operationId",access.id()), (r,i)->new SquadResponse(r.getObject("public_id",UUID.class),
                r.getObject("team_id",UUID.class),r.getString("name"),r.getString("acronym"),r.getString("description"),
                r.getInt("capacity"),r.getLong("participant_count"),r.getInt("sort_order"),r.getString("status"),
                r.getObject("commander_id",UUID.class),r.getString("commander_callsign"),r.getObject("radio_id",UUID.class),r.getString("radio_callsign")));
        List<TeamStructureResponse> teams = jdbc.query("""
            SELECT t.public_id,t.name,t.acronym,t.color,t.description,t.capacity,t.sort_order,t.status,
                   count(p.id) FILTER (WHERE p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')) participant_count,
                   cp.public_id commander_id,cp.callsign commander_callsign,rp.public_id radio_id,rp.callsign radio_callsign
            FROM operation_team t LEFT JOIN operation_participant p ON p.operation_team_id=t.id
            LEFT JOIN operation_role_assignment cr ON cr.operation_team_id=t.id AND cr.operation_squad_id IS NULL AND cr.role='TEAM_COMMANDER'
            LEFT JOIN operator_profile cp ON cp.user_id=cr.user_id
            LEFT JOIN operation_role_assignment rr ON rr.operation_team_id=t.id AND rr.operation_squad_id IS NULL AND rr.role='TEAM_RADIO'
            LEFT JOIN operator_profile rp ON rp.user_id=rr.user_id
            WHERE t.operation_id=:operationId GROUP BY t.id,t.sort_order,cp.public_id,cp.callsign,rp.public_id,rp.callsign ORDER BY t.sort_order
            """, Map.of("operationId",access.id()), (r,i)-> {
                UUID teamId=r.getObject("public_id",UUID.class);
                return new TeamStructureResponse(teamId,r.getString("name"),r.getString("acronym"),r.getString("color"),
                    r.getString("description"),r.getInt("capacity"),r.getLong("participant_count"),r.getInt("sort_order"),
                    r.getString("status"),r.getObject("commander_id",UUID.class),r.getString("commander_callsign"),
                    r.getObject("radio_id",UUID.class),r.getString("radio_callsign"),
                    squads.stream().filter(s->teamId.equals(s.teamId())).toList());
            });
        long count = jdbc.queryForObject("SELECT count(*) FROM operation_participant WHERE operation_id=:id AND status IN "+ACTIVE,
            Map.of("id",access.id()),Long.class);
        UUID currentUserSquadId=jdbc.query("""
            SELECT s.public_id FROM operation_participant p JOIN operation_squad s ON s.id=p.operation_squad_id
            WHERE p.operation_id=:operationId AND p.user_id=:userId AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')
            """,Map.of("operationId",access.id(),"userId",userId),(r,i)->r.getObject("public_id",UUID.class)).stream().findFirst().orElse(null);
        return new StructureResponse(access.publicId(),access.status(),access.gameSize(),access.participantLimit(),count,
            access.commandRolesEnabled(),access.allowRoleAccumulation(),access.owner()||access.operationAdmin(),currentUserSquadId,teams,roles);
    }
    public int updateSettings(OperationAccess a,UpdateStructureSettingsRequest r,String size,Instant now){int changed=jdbc.update("""
        UPDATE airsoft_operation SET game_size=:size,participant_limit=:limit,
          command_roles_enabled=CASE WHEN :size='SMALL' THEN FALSE ELSE :commands END,
          allow_role_accumulation=:accumulation,general_chat_public_read=:publicRead,updated_at=:now,version=version+1
        WHERE id=:id
        """,new MapSqlParameterSource().addValue("size",size).addValue("limit",r.participantLimit())
        .addValue("commands",r.commandRolesEnabled()).addValue("accumulation",r.allowRoleAccumulation())
        .addValue("publicRead",r.generalChatPublicRead()).addValue("now",Timestamp.from(now)).addValue("id",a.id()));
        if(changed==1)jdbc.update("UPDATE operation_chat_channel SET public_read=:publicRead,updated_at=:now WHERE operation_id=:id AND channel_type='GENERAL'",
            Map.of("publicRead",r.generalChatPublicRead(),"now",Timestamp.from(now),"id",a.id()));return changed;}
    public long roleCount(OperationAccess a,UUID operatorId){return jdbc.queryForObject("""
        SELECT count(*) FROM operation_role_assignment r JOIN operator_profile p ON p.user_id=r.user_id
        WHERE r.operation_id=:operationId AND p.public_id=:operatorId
        """,Map.of("operationId",a.id(),"operatorId",operatorId),Long.class);}

    public UUID createTeam(OperationAccess a, SaveTeamRequest r, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operation_team(operation_id,name,acronym,color,description,capacity,sort_order,status,updated_at)
            VALUES(:operationId,:name,:acronym,:color,:description,:capacity,
              COALESCE(:sortOrder,(SELECT COALESCE(max(sort_order),0)+1 FROM operation_team WHERE operation_id=:operationId)),
              :status,:now) RETURNING public_id
            """, teamParams(a,r,now).addValue("operationId",a.id()),UUID.class);
    }
    public int updateTeam(OperationAccess a, UUID teamId, SaveTeamRequest r, Instant now) {
        return jdbc.update("""
            UPDATE operation_team SET name=:name,acronym=:acronym,color=:color,description=:description,
              capacity=:capacity,sort_order=COALESCE(:sortOrder,sort_order),status=:status,updated_at=:now,version=version+1
            WHERE public_id=:teamId AND operation_id=:operationId
            """, teamParams(a,r,now).addValue("teamId",teamId).addValue("operationId",a.id()));
    }
    public int deleteEmptyTeam(OperationAccess a, UUID teamId) {
        return jdbc.update("""
            DELETE FROM operation_team t WHERE t.public_id=:teamId AND t.operation_id=:operationId
              AND NOT EXISTS(SELECT 1 FROM operation_participant p WHERE p.operation_team_id=t.id AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN'))
            """,
            Map.of("teamId",teamId,"operationId",a.id()));
    }
    public UUID createSquad(OperationAccess a, UUID teamId, SaveSquadRequest r, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operation_squad(operation_team_id,name,acronym,description,capacity,sort_order,status,updated_at)
            SELECT t.id,:name,:acronym,:description,:capacity,
              COALESCE(:sortOrder,(SELECT COALESCE(max(s.sort_order),0)+1 FROM operation_squad s WHERE s.operation_team_id=t.id)),
              :status,:now FROM operation_team t WHERE t.public_id=:teamId AND t.operation_id=:operationId RETURNING public_id
            """, squadParams(r,now).addValue("teamId",teamId).addValue("operationId",a.id()),UUID.class);
    }
    public int updateSquad(OperationAccess a, UUID squadId, SaveSquadRequest r, Instant now) {
        return jdbc.update("""
            UPDATE operation_squad s SET name=:name,acronym=:acronym,description=:description,capacity=:capacity,
              sort_order=COALESCE(:sortOrder,sort_order),status=:status,updated_at=:now,version=version+1
            FROM operation_team t WHERE s.operation_team_id=t.id AND s.public_id=:squadId AND t.operation_id=:operationId
            """, squadParams(r,now).addValue("squadId",squadId).addValue("operationId",a.id()));
    }
    public int deleteEmptySquad(OperationAccess a, UUID squadId) {
        return jdbc.update("""
            DELETE FROM operation_squad s USING operation_team t WHERE s.operation_team_id=t.id
              AND s.public_id=:squadId AND t.operation_id=:operationId
              AND NOT EXISTS(SELECT 1 FROM operation_participant p WHERE p.operation_squad_id=s.id AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN'))
            """,
            Map.of("squadId",squadId,"operationId",a.id()));
    }
    public void lockTeamAndSquad(OperationAccess a, UUID teamId, UUID squadId) {
        jdbc.queryForObject("SELECT id FROM operation_team WHERE public_id=:id AND operation_id=:op FOR UPDATE",Map.of("id",teamId,"op",a.id()),Long.class);
        if(squadId!=null) jdbc.queryForObject("SELECT s.id FROM operation_squad s JOIN operation_team t ON t.id=s.operation_team_id WHERE s.public_id=:id AND t.operation_id=:op FOR UPDATE",Map.of("id",squadId,"op",a.id()),Long.class);
    }
    public int moveParticipant(OperationAccess a, MoveParticipantRequest r, Instant now) {
        return jdbc.update("""
            UPDATE operation_participant p SET operation_team_id=t.id,operation_squad_id=s.id,updated_at=:now
            FROM operator_profile profile,operation_team t LEFT JOIN operation_squad s ON s.operation_team_id=t.id AND s.public_id=:squadId
            WHERE p.operation_id=:operationId AND p.user_id=profile.user_id AND profile.public_id=:operatorId
              AND t.operation_id=:operationId AND t.public_id=:teamId AND (:squadId IS NULL OR s.id IS NOT NULL)
              AND (SELECT count(*) FROM operation_participant x WHERE x.operation_team_id=t.id AND x.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')) < t.capacity
              AND (:squadId IS NULL OR (SELECT count(*) FROM operation_participant x WHERE x.operation_squad_id=s.id AND x.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')) < s.capacity
            """,
            new MapSqlParameterSource().addValue("operationId",a.id()).addValue("operatorId",r.operatorId())
                .addValue("teamId",r.teamId()).addValue("squadId",r.squadId()).addValue("now",Timestamp.from(now)));
    }

    public int assignRole(OperationAccess a, AssignRoleRequest r, long actor, Instant now) {
        return jdbc.update("""
            INSERT INTO operation_role_assignment(operation_id,user_id,operation_team_id,operation_squad_id,role,assigned_by,created_at)
            SELECT :operationId,p.user_id,t.id,s.id,:role,:actor,:now FROM operator_profile p
            LEFT JOIN operation_team t ON t.operation_id=:operationId AND t.public_id=:teamId
            LEFT JOIN operation_squad s ON s.operation_team_id=t.id AND s.public_id=:squadId
            WHERE p.public_id=:operatorId AND EXISTS(SELECT 1 FROM operation_participant op WHERE op.operation_id=:operationId AND op.user_id=p.user_id AND op.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN'))
            ON CONFLICT (operation_id,role,operation_team_id,operation_squad_id) DO UPDATE SET user_id=EXCLUDED.user_id,assigned_by=EXCLUDED.assigned_by,created_at=EXCLUDED.created_at
            """,
            new MapSqlParameterSource().addValue("operationId",a.id()).addValue("operatorId",r.operatorId())
                .addValue("teamId",r.teamId()).addValue("squadId",r.squadId()).addValue("role",r.role())
                .addValue("actor",actor).addValue("now",Timestamp.from(now)));
    }
    public int removeRole(OperationAccess a, UUID assignmentId) { return jdbc.update("DELETE FROM operation_role_assignment WHERE public_id=:id AND operation_id=:op",Map.of("id",assignmentId,"op",a.id())); }

    public ChatAccess chatAccess(OperationAccess a, long userId, UUID squadId) {
        var params=new MapSqlParameterSource().addValue("op",a.id()).addValue("user",userId).addValue("squadId",squadId);
        return jdbc.queryForObject("""
            SELECT c.id,c.public_id,c.channel_type,c.status,c.public_read,
              EXISTS(SELECT 1 FROM operation_participant p WHERE p.operation_id=:op AND p.user_id=:user AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')) participant,
              (CAST(:squadId AS uuid) IS NULL OR EXISTS(SELECT 1 FROM operation_participant p JOIN operation_squad s ON s.id=p.operation_squad_id
                WHERE p.operation_id=:op AND p.user_id=:user AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN') AND s.public_id=:squadId)) squad_member
            FROM operation_chat_channel c LEFT JOIN operation_squad s ON s.id=c.operation_squad_id
            WHERE c.operation_id=:op AND ((CAST(:squadId AS uuid) IS NULL AND c.channel_type='GENERAL') OR (c.channel_type='SQUAD' AND s.public_id=:squadId))
            """,params,(r,i)->new ChatAccess(r.getLong("id"),r.getObject("public_id",UUID.class),r.getString("channel_type"),
                r.getString("status"),r.getBoolean("public_read"),r.getBoolean("participant"),r.getBoolean("squad_member")));
    }
    public ChatPageResponse messages(ChatAccess c,long userId,int limit,Instant before) {
        var params=new MapSqlParameterSource().addValue("channel",c.id()).addValue("user",userId).addValue("limit",limit+1)
            .addValue("before",before==null?null:Timestamp.from(before));
        List<ChatMessageResponse> all=jdbc.query("""
            SELECT m.public_id,c.public_id channel_id,p.public_id author_id,p.callsign,p.display_name,
              user_team.name team_name,user_team.acronym team_acronym,m2.public_id parent_id,
              CASE WHEN m.status IN ('DELETED','HIDDEN') THEN '' ELSE m.body END body,m.status,m.official,m.created_at,m.updated_at,
              m.author_user_id=:user AND m.status IN ('VISIBLE','EDITED') AND m.created_at > now()-interval '15 minutes' editable
            FROM operation_chat_message m JOIN operation_chat_channel c ON c.id=m.channel_id
            JOIN operator_profile p ON p.user_id=m.author_user_id
            LEFT JOIN team_member membership ON membership.user_id=m.author_user_id AND membership.left_at IS NULL
            LEFT JOIN team user_team ON user_team.id=membership.team_id AND user_team.status='ACTIVE'
            LEFT JOIN operation_chat_message m2 ON m2.id=m.parent_message_id
            WHERE m.channel_id=:channel AND (CAST(:before AS timestamptz) IS NULL OR m.created_at<:before)
            ORDER BY m.created_at DESC,m.id DESC LIMIT :limit
            """,params,(r,i)->new ChatMessageResponse(r.getObject("public_id",UUID.class),r.getObject("channel_id",UUID.class),
                new ChatAuthor(r.getObject("author_id",UUID.class),r.getString("callsign"),r.getString("display_name"),
                    r.getString("team_name"),r.getString("team_acronym"),null),
                r.getObject("parent_id",UUID.class),r.getString("body"),r.getString("status"),r.getBoolean("official"),null,
                r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getBoolean("editable")));
        boolean more=all.size()>limit; if(more) all=all.subList(0,limit);
        return new ChatPageResponse(c.publicId(),c.type(),"LOCKED".equals(c.status()),all,more);
    }
    public UUID send(ChatAccess c,long userId,SendMessageRequest r,String body,Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operation_chat_message(channel_id,author_user_id,parent_message_id,body,official,idempotency_key,created_at,updated_at)
            SELECT :channel,:user,parent.id,:body,:official,:key,:now,:now FROM (SELECT 1) seed
            LEFT JOIN operation_chat_message parent ON parent.public_id=:parent AND parent.channel_id=:channel
            WHERE CAST(:parent AS uuid) IS NULL OR parent.id IS NOT NULL
            ON CONFLICT(author_user_id,idempotency_key) DO UPDATE SET updated_at=operation_chat_message.updated_at
            RETURNING public_id
            """,new MapSqlParameterSource().addValue("channel",c.id()).addValue("user",userId).addValue("parent",r.parentMessageId())
                .addValue("body",body).addValue("official",r.official()).addValue("key",r.idempotencyKey()).addValue("now",Timestamp.from(now)),UUID.class);
    }
    public int report(ChatAccess c,long userId,UUID messageId,String reason,Instant now) { return jdbc.update("""
        INSERT INTO operation_chat_message_report(message_id,reporter_user_id,reason,created_at)
        SELECT m.id,:user,:reason,:now FROM operation_chat_message m WHERE m.public_id=:message AND m.channel_id=:channel
        ON CONFLICT(message_id,reporter_user_id) DO NOTHING
        """,Map.of("user",userId,"reason",reason,"now",Timestamp.from(now),"message",messageId,"channel",c.id())); }
    public int moderate(ChatAccess c,long actor,UUID messageId,String status,String reason,Instant now) {
        int changed=jdbc.update("UPDATE operation_chat_message SET status=:status,deleted_at=CASE WHEN :status IN ('DELETED','HIDDEN') THEN :now ELSE NULL END,updated_at=:now WHERE public_id=:message AND channel_id=:channel",
            Map.of("status",status,"now",Timestamp.from(now),"message",messageId,"channel",c.id()));
        if(changed==1) jdbc.update("INSERT INTO operation_moderation_action(operation_id,actor_user_id,target_type,target_public_id,action,reason,created_at) SELECT operation_id,:actor,'CHAT_MESSAGE',:message,:action,:reason,:now FROM operation_chat_channel WHERE id=:channel",
            new MapSqlParameterSource().addValue("actor",actor).addValue("message",messageId).addValue("action",status)
                .addValue("reason",reason).addValue("now",Timestamp.from(now)).addValue("channel",c.id()));
        return changed;
    }
    public int setChannelState(ChatAccess c,boolean locked,Instant now) { return jdbc.update("UPDATE operation_chat_channel SET status=:status,updated_at=:now WHERE id=:id",Map.of("status",locked?"LOCKED":"OPEN","now",Timestamp.from(now),"id",c.id())); }

    private MapSqlParameterSource teamParams(OperationAccess a,SaveTeamRequest r,Instant now){return new MapSqlParameterSource()
        .addValue("name",clean(r.name())).addValue("acronym",nullable(r.acronym())).addValue("color",r.color().toUpperCase())
        .addValue("description",nullable(r.description())).addValue("capacity",r.capacity()).addValue("sortOrder",r.sortOrder())
        .addValue("status",r.entriesOpen()?"OPEN":"CLOSED").addValue("now",Timestamp.from(now));}
    private MapSqlParameterSource squadParams(SaveSquadRequest r,Instant now){return new MapSqlParameterSource()
        .addValue("name",clean(r.name())).addValue("acronym",nullable(r.acronym())).addValue("description",nullable(r.description()))
        .addValue("capacity",r.capacity()).addValue("sortOrder",r.sortOrder()).addValue("status",r.entriesOpen()?"OPEN":"CLOSED")
        .addValue("now",Timestamp.from(now));}
    private String clean(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");} private String nullable(String s){String v=clean(s);return v.isEmpty()?null:v;}
    public record OperationAccess(long id,UUID publicId,long ownerId,String status,String gameSize,Integer participantLimit,boolean commandRolesEnabled,boolean allowRoleAccumulation,boolean owner,boolean operationAdmin){}
    public record ChatAccess(long id,UUID publicId,String type,String status,boolean publicRead,boolean participant,boolean squadMember){}
}
