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
}
