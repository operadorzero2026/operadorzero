package br.com.operadorzero.notification;

import br.com.operadorzero.identity.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conquistas calculadas na hora a partir de dados ja validados: participacao confirmada em operacao finalizada,
 * desempenho confirmado por organizador ou equipe, vinculos de equipe e publicacoes ativas.
 */
@Service
public class AchievementService {
    private record Definition(String code, String name, String category, String description, String metric, int target) {}

    private static final List<Definition> CATALOG = List.of(
        new Definition("FIRST_STEP", "Primeiro Passo", "Participação", "Participe de uma operação até o fim, com presença confirmada.", "OPERATIONS", 1),
        new Definition("ACTIVE_OPERATOR", "Operador Ativo", "Participação", "Complete 5 operações com presença confirmada.", "OPERATIONS", 5),
        new Definition("FIELD_VETERAN", "Veterano de Campo", "Participação", "Complete 10 operações com presença confirmada.", "OPERATIONS", 10),
        new Definition("EXPERIENCED", "Operador Experiente", "Participação", "Complete 25 operações com presença confirmada.", "OPERATIONS", 25),
        new Definition("CENTURION", "Centurião", "Participação", "Complete 100 operações com presença confirmada.", "OPERATIONS", 100),
        new Definition("FIRST_ELIMINATION", "Primeira Eliminação", "Combate", "Tenha uma eliminação em desempenho confirmado.", "ELIMINATIONS", 1),
        new Definition("TEN_CONFIRMED", "Dez Confirmadas", "Combate", "Some 10 eliminações em desempenhos confirmados.", "ELIMINATIONS", 10),
        new Definition("HUNDRED_MARK", "Marca de Cem", "Combate", "Some 100 eliminações em desempenhos confirmados.", "ELIMINATIONS", 100),
        new Definition("FIRST_OBJECTIVE", "Primeiro Objetivo", "Objetivos", "Cumpra um objetivo em desempenho confirmado.", "OBJECTIVES", 1),
        new Definition("OBJECTIVE_SPECIALIST", "Especialista em Objetivos", "Objetivos", "Some 25 objetivos em desempenhos confirmados.", "OBJECTIVES", 25),
        new Definition("FIRST_TEAM", "Primeira Equipe", "Equipe", "Entre ou crie uma equipe.", "TEAMS_JOINED", 1),
        new Definition("FOUNDER", "Fundador", "Equipe", "Crie uma equipe na plataforma.", "TEAMS_FOUNDED", 1),
        new Definition("FIRST_ORGANIZED", "Primeiro Comando", "Organização", "Organize uma operação até a finalização.", "OPERATIONS_ORGANIZED", 1),
        new Definition("ORGANIZER", "Organizador Experiente", "Organização", "Organize 10 operações até a finalização.", "OPERATIONS_ORGANIZED", 10),
        new Definition("FIRST_POST", "Primeira Voz", "Comunidade", "Publique na Comunidade.", "POSTS", 1),
        new Definition("COMMUNITY_VOICE", "Voz da Comunidade", "Comunidade", "Mantenha 10 publicações ativas na Comunidade.", "POSTS", 10));

    private final NamedParameterJdbcTemplate jdbc;

    public AchievementService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AchievementPage mine(AuthenticatedUser user) {
        Map<String, Long> metrics = metrics(user.internalId());
        List<AchievementResponse> items = CATALOG.stream().map(d -> {
            long current = Math.min(metrics.getOrDefault(d.metric(), 0L), d.target());
            return new AchievementResponse(d.code(), d.name(), d.category(), d.description(), current, d.target(), current >= d.target());
        }).toList();
        return new AchievementPage(items, items.stream().filter(AchievementResponse::unlocked).count());
    }

    Map<String, Long> metrics(long userId) {
        return jdbc.queryForObject("""
            SELECT
              (SELECT count(*) FROM operation_participant p JOIN airsoft_operation o ON o.id = p.operation_id
                 WHERE p.user_id = :userId AND p.status IN ('CONFIRMED','CHECKED_IN') AND o.status = 'FINISHED' AND o.deleted_at IS NULL) operations,
              (SELECT COALESCE(sum(r.eliminations), 0) FROM performance_record r
                 WHERE r.user_id = :userId AND r.status IN ('ORGANIZER_CONFIRMED','TEAM_CONFIRMED','CORRECTED')) eliminations,
              (SELECT COALESCE(sum(r.objectives_completed), 0) FROM performance_record r
                 WHERE r.user_id = :userId AND r.status IN ('ORGANIZER_CONFIRMED','TEAM_CONFIRMED','CORRECTED')) objectives,
              (SELECT count(DISTINCT team_id) FROM team_membership_history
                 WHERE user_id = :userId AND action IN ('TEAM_CREATED','JOINED')) teams_joined,
              (SELECT count(*) FROM team_membership_history WHERE user_id = :userId AND action = 'TEAM_CREATED') teams_founded,
              (SELECT count(*) FROM airsoft_operation WHERE organizer_user_id = :userId AND status = 'FINISHED' AND deleted_at IS NULL) operations_organized,
              (SELECT count(*) FROM community_post WHERE author_user_id = :userId AND status = 'PUBLISHED') posts
            """, Map.of("userId", userId), (row, index) -> Map.of(
                "OPERATIONS", row.getLong("operations"), "ELIMINATIONS", row.getLong("eliminations"),
                "OBJECTIVES", row.getLong("objectives"), "TEAMS_JOINED", row.getLong("teams_joined"),
                "TEAMS_FOUNDED", row.getLong("teams_founded"), "OPERATIONS_ORGANIZED", row.getLong("operations_organized"),
                "POSTS", row.getLong("posts")));
    }

    public record AchievementResponse(String code, String name, String category, String description, long current,
                                      long target, boolean unlocked) {}

    public record AchievementPage(List<AchievementResponse> items, long unlocked) {}
}
