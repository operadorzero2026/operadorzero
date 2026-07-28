package br.com.operadorzero.ranking;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RankingRepository {
    private static final String GROSS = "(10 + 3 * pr.eliminations - pr.deaths + 8 * pr.objectives_completed + CASE pr.result WHEN 'WIN' THEN 15 WHEN 'DRAW' THEN 5 ELSE 0 END)";
    private final NamedParameterJdbcTemplate jdbc;
    public RankingRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<RankingRow> operatorTotals(String city, String stateCode, UUID teamId, int limit) {
        String sql = """
            SELECT p.public_id operator_id,u.username,p.display_name,p.callsign,p.city,p.state_code,
              t.public_id team_id,t.name team_name,t.acronym team_acronym,
              count(*) valid_operations,sum(pr.eliminations) eliminations,sum(pr.deaths) deaths,
              sum(pr.objectives_completed) objectives,
              sum(CASE WHEN pr.result='WIN' THEN 1 ELSE 0 END) wins,
              sum(CASE WHEN pr.result='DRAW' THEN 1 ELSE 0 END) draws,
              sum(CASE WHEN pr.organizer_confirmed THEN %s ELSE 0 END) organizer_gross,
              sum(CASE WHEN pr.team_confirmed THEN %s ELSE 0 END) team_gross,
              sum(pr.penalty_points) penalties
            FROM performance_record pr
            JOIN airsoft_operation o ON o.id=pr.operation_id AND o.status='FINISHED'
            JOIN operation_participant op ON op.operation_id=o.id AND op.user_id=pr.user_id AND op.status IN ('CONFIRMED','CHECKED_IN')
            JOIN app_user u ON u.id=pr.user_id AND u.status='ACTIVE'
            JOIN operator_profile p ON p.user_id=u.id
            LEFT JOIN team_member tm ON tm.user_id=u.id AND tm.left_at IS NULL
            LEFT JOIN team t ON t.id=tm.team_id AND t.status='ACTIVE'
            WHERE pr.status NOT IN ('REJECTED','CONTESTED') AND pr.ranking_rule_version=:ruleVersion
              AND (:city='' OR lower(p.city)=lower(:city)) AND (:stateCode='' OR p.state_code=upper(:stateCode))
              AND (CAST(:teamId AS uuid) IS NULL OR t.public_id=:teamId)
            GROUP BY p.public_id,u.username,p.display_name,p.callsign,p.city,p.state_code,t.public_id,t.name,t.acronym
            LIMIT :limit
            """.formatted(GROSS, GROSS);
        return jdbc.query(sql, new MapSqlParameterSource().addValue("ruleVersion", RankingCalculator.RULE_VERSION)
            .addValue("city", city).addValue("stateCode", stateCode).addValue("teamId", teamId).addValue("limit", limit),
            (r,i)->new RankingRow(r.getObject("operator_id",UUID.class),r.getString("username"),r.getString("display_name"),
                r.getString("callsign"),r.getString("city"),r.getString("state_code"),r.getObject("team_id",UUID.class),
                r.getString("team_name"),r.getString("team_acronym"),r.getInt("valid_operations"),r.getLong("eliminations"),
                r.getLong("deaths"),r.getLong("objectives"),r.getLong("wins"),r.getLong("draws"),
                value(r.getBigDecimal("organizer_gross")),value(r.getBigDecimal("team_gross")),value(r.getBigDecimal("penalties"))));
    }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    public record RankingRow(UUID operatorId,String username,String displayName,String callsign,String city,String stateCode,
        UUID teamId,String teamName,String teamAcronym,int validOperations,long eliminations,long deaths,long objectives,
        long wins,long draws,BigDecimal organizerGross,BigDecimal teamGross,BigDecimal penalties) {}
}
