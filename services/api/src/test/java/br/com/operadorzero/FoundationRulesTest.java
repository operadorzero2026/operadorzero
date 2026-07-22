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
    void migrationsUseConstraintsAndNoDestructiveStatements() throws Exception {
        String v1 = Files.readString(Path.of("src/main/resources/db/migration/V1__identity_access_foundation.sql"));
        String v2 = Files.readString(Path.of("src/main/resources/db/migration/V2__operator_profile_privacy.sql"));
        String v3 = Files.readString(Path.of("src/main/resources/db/migration/V3__functional_identity.sql"));
        assertThat(v1).contains("UNIQUE", "REFERENCES", "CHECK", "audit_event");
        assertThat(v2).contains("operator_privacy_setting", "ONLY_ME", "PUBLIC");
        assertThat(v3).contains("auth_token", "user_session", "user_oidc_identity", "token_hash", "RESET_PASSWORD");
        assertThat(v1 + v2 + v3).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
    }
}
