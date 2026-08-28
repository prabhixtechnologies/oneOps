package com.prabhix.platform.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
public class FlywaySchemaValidationIntegrationTest extends IntegrationTestBase {

    @Autowired
    Flyway flyway;

    @Test
    void flywayMigratesCleanlyAndHibernateValidateAgrees() {
        var info = flyway.info();

        for (MigrationInfo migration : info.all()) {
            assertThat(migration.getState())
                    .as("Migration %s version %s", migration.getDescription(), migration.getVersion())
                    .isNotIn(MigrationState.FAILED, MigrationState.OUT_OF_ORDER);
        }

        assertThat(info.pending())
                .as("Pending migrations after context startup indicate incomplete deploy")
                .isEmpty();
    }
}
