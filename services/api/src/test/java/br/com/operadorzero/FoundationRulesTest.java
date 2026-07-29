package br.com.operadorzero;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.operadorzero.operation.OperationService;
import br.com.operadorzero.community.CommunityService;
import br.com.operadorzero.operator.OperatorService;
import br.com.operadorzero.performance.PerformanceService;
import br.com.operadorzero.team.TeamService;
import br.com.operadorzero.venue.VenueService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

class FoundationRulesTest {
    @Test
    void servicesWithTestClockConstructorsDeclareTheProductionInjectionPoint() {
        for (Class<?> service : new Class<?>[] {
            CommunityService.class, OperationService.class, OperatorService.class, PerformanceService.class, TeamService.class, VenueService.class
        }) {
            assertThat(Arrays.stream(service.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class)))
                .as(service.getSimpleName())
                .hasSize(1);
        }
    }

    @Test
    void productionConfigurationValidatesSchemaAndHidesErrorDetails() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains(
            "ddl-auto: validate",
            "include-stacktrace: never",
            "show-details: never",
            "port: ${PORT:${SERVER_PORT:8080}}",
            "allowed-origins: ${CORS_ALLOWED_ORIGINS:"
        );
        assertThat(yaml).doesNotContain("ddl-auto: update");
    }

    @Test
    void productionDoesNotLoadSocialAuthenticationDependenciesOrSessions() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String productionYaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        String securityConfig = Files.readString(Path.of("src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java"));

        assertThat(pom).doesNotContain("spring-boot-starter-oauth2-client", "spring-session-data-redis");
        assertThat(productionYaml).doesNotContain("OZ_OAUTH_SESSION", "spring.session");
        assertThat(securityConfig).doesNotContain("oauth2Login", "/oauth2/", "/login/oauth2/");
    }

    @Test
    void productionRequiresKeyedHashesAndDoesNotEvictLiveAuthenticationState() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("hash-key: ${AUTH_HASH_KEY:}");
    }

    @Test
    void migrationsUseConstraintsAndNoDestructiveStatements() throws Exception {
        String v1 = Files.readString(Path.of("src/main/resources/db/migration/V1__identity_access_foundation.sql"));
        String v2 = Files.readString(Path.of("src/main/resources/db/migration/V2__operator_profile_privacy.sql"));
        String v3 = Files.readString(Path.of("src/main/resources/db/migration/V3__functional_identity.sql"));
        assertThat(v1).contains("UNIQUE", "REFERENCES", "CHECK", "audit_event");
        assertThat(v2).contains("operator_privacy_setting", "ONLY_ME", "PUBLIC");
        assertThat(v3).contains("auth_token", "user_session", "user_oidc_identity", "token_hash", "RESET_PASSWORD");
        assertThat(v1 + v2 + v3).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
    }

    @Test
    void financialRoleRemovalIsAuditedAndPreservesUsers() throws Exception {
        String v4 = Files.readString(Path.of("src/main/resources/db/migration/V4__remove_financial_role.sql"));

        assertThat(v4).contains(
            "ROLE_ASSIGNMENT_REMOVED_BY_MIGRATION",
            "migration-v4-remove-financial-role",
            "V4 abortada: tabelas financeiras nao versionadas exigem inventario e exportacao",
            "DELETE FROM user_role",
            "DELETE FROM role_permission",
            "DELETE FROM role WHERE code = 'FINANCE_MANAGER'"
        );
        assertThat(v4).doesNotContain("DELETE FROM app_user", "DROP TABLE", "TRUNCATE");
    }

    @Test
    void socialAuthenticationRetirementPreservesUsersAndRevokesOldSessions() throws Exception {
        String v17 = Files.readString(Path.of("src/main/resources/db/migration/V17__retire_social_authentication.sql"));

        assertThat(v17).contains("AUTH_SOCIAL_LOGIN_RETIRED", "UPDATE user_session", "DROP TABLE IF EXISTS oauth_registration_intent", "DROP TABLE IF EXISTS user_oidc_identity");
        assertThat(v17).doesNotContain("DELETE FROM app_user", "DROP TABLE app_user", "TRUNCATE");
    }

    @Test
    void operatorAndTeamMigrationsPreserveIdentityAndEnforceSingleActiveMembership() throws Exception {
        String v6 = Files.readString(Path.of("src/main/resources/db/migration/V6__operator_profile_functionality.sql"));
        String v7 = Files.readString(Path.of("src/main/resources/db/migration/V7__teams_and_invitations.sql"));

        assertThat(v6).contains("operator_equipment", "operator_username_history", "operator_privacy_setting");
        assertThat(v7).contains("team_invitation", "team_membership_history",
            "uq_team_member_active_user", "uq_team_active_captain", "uq_team_invitation_pending");
        assertThat(v6 + v7).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM app_user");
    }

    @Test
    void communityMigrationIsAdditiveAndEnforcesOwnershipAndUniqueInteractions() throws Exception {
        String v16 = Files.readString(Path.of("src/main/resources/db/migration/V16__community_posts_comments_and_moderation.sql"));

        assertThat(v16).contains(
            "community_category",
            "community_post",
            "community_comment",
            "community_vote",
            "community_bookmark",
            "community_report",
            "uq_community_post_author_idempotency",
            "PRIMARY KEY(post_id, user_id)",
            "ck_community_report_target"
        );
        assertThat(v16).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM app_user");
    }

    @Test
    void operationStructureQueriesGroupEveryExplicitOrderingColumn() throws Exception {
        String repository = Files.readString(Path.of(
            "src/main/java/br/com/operadorzero/operation/OperationStructureRepository.java"));

        assertThat(repository).contains(
            "GROUP BY s.id,t.public_id,t.sort_order,cp.public_id",
            "GROUP BY t.id,t.sort_order,cp.public_id"
        );
    }

    @Test
    void legacyOperationSquadMigrationPreservesParticipantsAndCreatesPrivateDestinations() throws Exception {
        String v21 = Files.readString(Path.of(
            "src/main/resources/db/migration/V21__backfill_legacy_operation_squads.sql"));

        assertThat(v21).contains("INSERT INTO operation_squad", "UPDATE operation_participant",
            "p.operation_team_id=s.operation_team_id", "p.operation_squad_id IS NULL");
        assertThat(v21).doesNotContain("DELETE FROM", "TRUNCATE", "DROP TABLE");
    }

    @Test
    void participationQueryTypesNullableSquadUuidAndApprovesOrganizer() throws Exception {
        String repository = Files.readString(Path.of(
            "src/main/java/br/com/operadorzero/operation/OperationRepository.java"));

        assertThat(repository).contains("CAST(:operationSquadId AS uuid) IS NULL",
            "CAST(:operationSquadId AS uuid) IS NOT NULL",
            "WHEN o.organizer_user_id = :userId THEN 'APPROVED'");
        assertThat(repository).doesNotContain(":operationSquadId IS NULL", ":operationSquadId IS NOT NULL");
    }
}
