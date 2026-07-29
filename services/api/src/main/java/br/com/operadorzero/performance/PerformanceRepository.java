package br.com.operadorzero.performance;

import br.com.operadorzero.performance.PerformanceDtos.PerformanceResponse;
import br.com.operadorzero.performance.PerformanceDtos.ReviewPerformanceRequest;
import br.com.operadorzero.performance.PerformanceDtos.SavePerformanceRequest;
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
public class PerformanceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public PerformanceRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    public Optional<OperationAccess> operationAccess(UUID operationId,long userId){
        try{return Optional.ofNullable(jdbc.queryForObject("""
          SELECT o.id,o.public_id,o.name,o.status,o.organizer_user_id,op.status participant_status,
            tm.team_id,tm.role team_role,t.public_id team_public_id
          FROM airsoft_operation o
          LEFT JOIN operation_participant op ON op.operation_id=o.id AND op.user_id=:userId
          LEFT JOIN team_member tm ON tm.user_id=:userId AND tm.left_at IS NULL
          LEFT JOIN team t ON t.id=tm.team_id AND t.status='ACTIVE'
          WHERE o.public_id=:operationId AND o.deleted_at IS NULL
          """,Map.of("operationId",operationId,"userId",userId),(r,i)->new OperationAccess(r.getLong("id"),r.getObject("public_id",UUID.class),r.getString("name"),r.getString("status"),r.getLong("organizer_user_id"),r.getString("participant_status"),(Long)r.getObject("team_id"),r.getString("team_role"),r.getObject("team_public_id",UUID.class))));}catch(EmptyResultDataAccessException e){return Optional.empty();}}

    public Optional<Long> teamInternalId(UUID teamId,long userId){if(teamId==null)return Optional.empty();try{return Optional.ofNullable(jdbc.queryForObject("""
      SELECT t.id FROM team t JOIN team_member tm ON tm.team_id=t.id AND tm.user_id=:userId AND tm.left_at IS NULL
      WHERE t.public_id=:teamId AND t.status='ACTIVE'""",Map.of("teamId",teamId,"userId",userId),Long.class));}catch(EmptyResultDataAccessException e){return Optional.empty();}}

    public UUID save(long operationId,long userId,Long teamId,SavePerformanceRequest r,String result,String scope,Instant now){
        return jdbc.queryForObject("""
          INSERT INTO performance_record(operation_id,user_id,represented_team_id,eliminations,deaths,objectives_completed,
            round_wins,result,position_used,notes,highlight_received,penalty_points,abandoned,participation_scope,status,
            ranking_rule_version,created_at,updated_at,version)
          VALUES(:operationId,:userId,:teamId,:eliminations,:deaths,:objectives,:roundWins,:result,:position,:notes,
            :highlight,:penalties,:abandoned,:scope,'SELF_DECLARED',:ruleVersion,:now,:now,0)
          ON CONFLICT(operation_id,user_id) DO UPDATE SET represented_team_id=excluded.represented_team_id,
            eliminations=excluded.eliminations,deaths=excluded.deaths,objectives_completed=excluded.objectives_completed,
            round_wins=excluded.round_wins,result=excluded.result,position_used=excluded.position_used,notes=excluded.notes,
            highlight_received=excluded.highlight_received,penalty_points=excluded.penalty_points,abandoned=excluded.abandoned,
            participation_scope=excluded.participation_scope,status='SELF_DECLARED',organizer_confirmed=FALSE,
            team_confirmed=FALSE,organizer_notes=NULL,updated_at=:now,version=performance_record.version+1
          WHERE performance_record.version=:version AND performance_record.status IN ('PENDING','SELF_DECLARED','CORRECTED')
          RETURNING public_id
          """,params(operationId,userId,teamId,r,result,scope,now),UUID.class);
    }

    public List<PerformanceResponse> mine(long userId){return query("pr.user_id=:viewerId",userId,Map.of("viewerId",userId));}
    public List<PerformanceResponse> reviewable(long userId){return query("(o.organizer_user_id=:viewerId OR EXISTS(SELECT 1 FROM team_member reviewer WHERE reviewer.user_id=:viewerId AND reviewer.team_id=pr.represented_team_id AND reviewer.left_at IS NULL AND reviewer.role IN ('CAPTAIN','MANAGER')))",userId,Map.of("viewerId",userId));}
    public Optional<PerformanceResponse> find(UUID id,long userId){List<PerformanceResponse> rows=query("pr.public_id=:id",userId,Map.of("id",id));return rows.stream().findFirst();}
    public Optional<RecordRef> recordRef(UUID id){try{return Optional.ofNullable(jdbc.queryForObject("SELECT id,operation_id,user_id FROM performance_record WHERE public_id=:id",Map.of("id",id),(r,i)->new RecordRef(r.getLong("id"),r.getLong("operation_id"),r.getLong("user_id")))) ;}catch(EmptyResultDataAccessException e){return Optional.empty();}}

    public int review(UUID id,long expectedVersion,ReviewPerformanceRequest r,String action,long reviewerId,boolean organizer,boolean team,Instant now){
        String status=switch(action){case "CONFIRM_ORGANIZER"->"ORGANIZER_CONFIRMED";case "CONFIRM_TEAM"->"TEAM_CONFIRMED";case "CORRECT"->"CORRECTED";default->"REJECTED";};
        return jdbc.update("""
          UPDATE performance_record SET
            eliminations=COALESCE(:eliminations,eliminations),deaths=COALESCE(:deaths,deaths),
            objectives_completed=COALESCE(:objectives,objectives_completed),round_wins=COALESCE(:roundWins,round_wins),
            result=COALESCE(:result,result),penalty_points=COALESCE(:penalties,penalty_points),organizer_notes=:notes,
            organizer_confirmed=organizer_confirmed OR :organizer,team_confirmed=team_confirmed OR :team,
            status=:status,updated_at=:now,version=version+1
          WHERE public_id=:id AND version=:version
          """,new MapSqlParameterSource().addValue("id",id).addValue("version",expectedVersion).addValue("eliminations",r.eliminations())
          .addValue("deaths",r.deaths()).addValue("objectives",r.objectivesCompleted()).addValue("roundWins",r.roundWins())
          .addValue("result",nullable(r.result())).addValue("penalties",r.penaltyPoints()).addValue("notes",nullable(r.notes()))
          .addValue("organizer",organizer).addValue("team",team).addValue("status",status).addValue("now",Timestamp.from(now)));
    }

    public boolean participated(long operationId,long userId){Integer count=jdbc.queryForObject("SELECT count(*) FROM operation_participant WHERE operation_id=:operationId AND user_id=:userId AND status IN ('CONFIRMED','CHECKED_IN')",Map.of("operationId",operationId,"userId",userId),Integer.class);return count!=null&&count>0;}
    public UUID contest(long recordId,long userId,String reason,Instant now){return jdbc.queryForObject("""
      INSERT INTO performance_contest(performance_record_id,contestant_user_id,reason,created_at)
      VALUES(:recordId,:userId,:reason,:now) RETURNING public_id""",Map.of("recordId",recordId,"userId",userId,"reason",reason,"now",Timestamp.from(now)),UUID.class);}
    public void markContested(long recordId,Instant now){jdbc.update("UPDATE performance_record SET status='CONTESTED',updated_at=:now,version=version+1 WHERE id=:id",Map.of("id",recordId,"now",Timestamp.from(now)));}

    private List<PerformanceResponse> query(String where,long viewerId,Map<String,?> extra){MapSqlParameterSource p=new MapSqlParameterSource().addValue("viewerId",viewerId);extra.forEach(p::addValue);return jdbc.query("""
      SELECT pr.*,o.public_id operation_public_id,o.name operation_name,o.operation_date,o.organizer_user_id,
        op_profile.public_id operator_public_id,op_profile.callsign operator_callsign,t.public_id team_public_id,t.name team_name,
        EXISTS(SELECT 1 FROM team_member reviewer WHERE reviewer.user_id=:viewerId AND reviewer.team_id=pr.represented_team_id AND reviewer.left_at IS NULL AND reviewer.role IN ('CAPTAIN','MANAGER')) can_team_review,
        EXISTS(SELECT 1 FROM operation_participant vp WHERE vp.operation_id=pr.operation_id AND vp.user_id=:viewerId AND vp.status IN ('CONFIRMED','CHECKED_IN')) can_contest
      FROM performance_record pr JOIN airsoft_operation o ON o.id=pr.operation_id
      JOIN operator_profile op_profile ON op_profile.user_id=pr.user_id LEFT JOIN team t ON t.id=pr.represented_team_id
      WHERE """+where+" ORDER BY o.operation_date DESC,pr.updated_at DESC",p,(r,i)->map(r,viewerId));}
    private PerformanceResponse map(ResultSet r,long viewerId)throws SQLException{return new PerformanceResponse(r.getObject("public_id",UUID.class),r.getObject("operation_public_id",UUID.class),r.getString("operation_name"),r.getDate("operation_date").toLocalDate(),r.getObject("operator_public_id",UUID.class),r.getString("operator_callsign"),r.getObject("team_public_id",UUID.class),r.getString("team_name"),r.getInt("eliminations"),r.getInt("deaths"),r.getInt("objectives_completed"),r.getInt("round_wins"),r.getString("result"),r.getString("position_used"),r.getString("notes"),r.getString("highlight_received"),r.getBigDecimal("penalty_points"),r.getBoolean("abandoned"),r.getString("participation_scope"),r.getString("status"),r.getBoolean("organizer_confirmed"),r.getBoolean("team_confirmed"),r.getString("organizer_notes"),r.getLong("user_id")==viewerId,r.getLong("organizer_user_id")==viewerId,r.getBoolean("can_team_review"),r.getBoolean("can_contest")&&r.getLong("user_id")!=viewerId,r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getLong("version"));}
    private MapSqlParameterSource params(long operationId,long userId,Long teamId,SavePerformanceRequest r,String result,String scope,Instant now){return new MapSqlParameterSource().addValue("operationId",operationId).addValue("userId",userId).addValue("teamId",teamId).addValue("eliminations",r.eliminations()).addValue("deaths",r.deaths()).addValue("objectives",r.objectivesCompleted()).addValue("roundWins",r.roundWins()).addValue("result",result).addValue("position",nullable(r.positionUsed())).addValue("notes",nullable(r.notes())).addValue("highlight",nullable(r.highlightReceived())).addValue("penalties",r.penaltyPoints()).addValue("abandoned",r.abandoned()).addValue("scope",scope).addValue("ruleVersion",br.com.operadorzero.ranking.RankingCalculator.RULE_VERSION).addValue("now",Timestamp.from(now)).addValue("version",r.version());}
    private String nullable(String v){if(v==null)return null;String n=v.trim();return n.isEmpty()?null:n;}
    public record OperationAccess(long id,UUID publicId,String name,String status,long organizerUserId,String participantStatus,Long teamId,String teamRole,UUID teamPublicId){}
    public record RecordRef(long id,long operationId,long userId){}
}
