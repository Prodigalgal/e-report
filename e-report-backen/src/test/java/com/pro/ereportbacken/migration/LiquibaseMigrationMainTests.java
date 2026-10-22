package com.pro.ereportbacken.migration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiquibaseMigrationMainTests {

    @Test
    void readsCompleteMigrationConfigurationWithoutChangingPassword() {
        Map<String, String> environment = completeEnvironment();
        environment.put("E_REPORT_MIGRATION_PASSWORD", " password with spaces ");

        var configuration = LiquibaseMigrationMain.MigrationConfiguration.fromEnvironment(environment);

        assertThat(configuration.url()).isEqualTo("jdbc:mysql://database/migration");
        assertThat(configuration.password()).isEqualTo(" password with spaces ");
        assertThat(configuration.changeLog()).isEqualTo("db/changelog/db.changelog-master.yaml");
        assertThat(configuration.parameters())
                .containsEntry("defaultTenantCode", "DEFAULT_CITY")
                .containsEntry("defaultTenantRootOrgName", "市公司本部")
                .containsEntry("defaultAdminUsername", "admin");
    }

    @Test
    void rejectsMissingDefaultTenantParameterBeforeOpeningDatabase() {
        Map<String, String> environment = completeEnvironment();
        environment.remove("DEFAULT_TENANT_NAME");

        assertThatThrownBy(() -> LiquibaseMigrationMain.MigrationConfiguration.fromEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEFAULT_TENANT_NAME");
    }

    @Test
    void keepsPostgreSqlBaselineMigrationParameterFree() {
        Map<String, String> environment = new HashMap<>();
        environment.put("E_REPORT_MIGRATION_URL", "jdbc:postgresql://database/migration");
        environment.put("E_REPORT_MIGRATION_USERNAME", "migrator");
        environment.put("E_REPORT_MIGRATION_PASSWORD", "secret");
        environment.put(
                "E_REPORT_MIGRATION_CHANGELOG",
                "db/changelog/postgresql/db.changelog-master.yaml"
        );

        var configuration = LiquibaseMigrationMain.MigrationConfiguration.fromEnvironment(environment);

        assertThat(configuration.changeLog())
                .isEqualTo("db/changelog/postgresql/db.changelog-master.yaml");
        assertThat(configuration.parameters()).isEmpty();
    }

    @Test
    void explicitlyLoadsBothDriversBundledInTheDatabaseAdminArtifact() {
        assertThatCode(() -> LiquibaseMigrationMain.loadSupportedJdbcDriver("jdbc:mysql://database/migration"))
                .doesNotThrowAnyException();
        assertThatCode(() -> LiquibaseMigrationMain.loadSupportedJdbcDriver("jdbc:postgresql://database/migration"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsJdbcProtocolsThatAreNotBundledInTheDatabaseAdminArtifact() {
        assertThatThrownBy(() -> LiquibaseMigrationMain.loadSupportedJdbcDriver("jdbc:h2:mem:migration"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported database migration JDBC URL");
    }

    @Test
    void acceptsCompletePrivateCountyNormalizationParameters() {
        Map<String, String> environment = countyNormalizationEnvironment();

        assertThat(LiquibaseMigrationMain.MigrationConfiguration.fromEnvironment(environment).parameters())
                .containsEntry("countyAdministratorUsernameA", "county_admin_a")
                .containsEntry("countyAdministratorNameC", "示例管理员丙");
    }

    @Test
    void rejectsPartiallyConfiguredCountyNormalization() {
        Map<String, String> environment = completeEnvironment();
        environment.put("COUNTY_ADMINISTRATOR_USERNAME_A", "county_admin_a");

        assertThatThrownBy(() -> LiquibaseMigrationMain.MigrationConfiguration.fromEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("all six");
    }

    @Test
    void rejectsSqlLiteralAndControlCharactersInPrivateIdentityParameters() {
        for (String invalid : new String[] {"admin'", "admin\\", "ad\nmin", "a".repeat(101)}) {
            Map<String, String> environment = countyNormalizationEnvironment();
            environment.put("COUNTY_ADMINISTRATOR_USERNAME_A", invalid);

            assertThatThrownBy(() -> LiquibaseMigrationMain.MigrationConfiguration.fromEnvironment(environment))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("COUNTY_ADMINISTRATOR_USERNAME_A");
        }
    }

    private Map<String, String> countyNormalizationEnvironment() {
        Map<String, String> environment = completeEnvironment();
        String[] names = {"示例管理员甲", "示例管理员乙", "示例管理员丙"};
        for (int index = 0; index < names.length; index++) {
            char suffix = (char) ('A' + index);
            environment.put("COUNTY_ADMINISTRATOR_USERNAME_" + suffix, "county_admin_" + Character.toLowerCase(suffix));
            environment.put("COUNTY_ADMINISTRATOR_NAME_" + suffix, names[index]);
        }
        return environment;
    }

    private Map<String, String> completeEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("E_REPORT_MIGRATION_URL", "jdbc:mysql://database/migration");
        environment.put("E_REPORT_MIGRATION_USERNAME", "root");
        environment.put("E_REPORT_MIGRATION_PASSWORD", "secret");
        environment.put("DEFAULT_TENANT_GROUP_CODE", "DEFAULT_PROVINCE");
        environment.put("DEFAULT_TENANT_GROUP_NAME", "默认省公司");
        environment.put("DEFAULT_TENANT_CODE", "DEFAULT_CITY");
        environment.put("DEFAULT_TENANT_NAME", "默认市公司");
        environment.put("DEFAULT_TENANT_ROOT_ORG_NAME", "市公司本部");
        environment.put("DEFAULT_ADMIN_USERNAME", "admin");
        return environment;
    }
}
