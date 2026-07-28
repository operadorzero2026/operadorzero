package br.com.operadorzero;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FoundationRulesTest {
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
    void productionPersistsTemporaryOauthSessionInRedis() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String productionYaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(pom).contains("spring-session-data-redis");
        assertThat(productionYaml).contains(
            "timeout: 10m",
            "namespace: operador-zero:oauth-session",
            "repository-type: default",
            "name: OZ_OAUTH_SESSION"
        );
    }

    @Test
    void productionRequiresKeyedHashesAndDoesNotEvictLiveAuthenticationState() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String render = Files.readString(Path.of("../../render.yaml"));

        assertThat(yaml).contains("hash-key: ${AUTH_HASH_KEY:}");
        assertThat(render).contains("key: AUTH_HASH_KEY", "maxmemoryPolicy: noeviction")
            .doesNotContain("maxmemoryPolicy: allkeys-lru");
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
    void oauthIntentCleanupHasAnIndexedExpirationPath() throws Exception {
        String v5 = Files.readString(Path.of("src/main/resources/db/migration/V5__oauth_intent_cleanup_index.sql"));

        assertThat(v5).contains("oauth_registration_intent", "expires_at", "consumed_at", "CREATE INDEX");
        assertThat(v5).doesNotContain("DROP TABLE", "TRUNCATE");
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
}
