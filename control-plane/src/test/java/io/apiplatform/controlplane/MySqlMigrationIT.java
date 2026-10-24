package io.apiplatform.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the Flyway migrations against a real MySQL 8 to catch dialect drift from the H2-based tests.
 * Skipped automatically when Docker is not available (see docs/testing-strategy.md).
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("apiplatform");

    @Test
    void migrationsApplyCleanlyAndCreateExpectedIndexes() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load();
        assertThat(flyway.migrate().success).isTrue();
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet rs = c.createStatement().executeQuery(
                     "select count(*) from information_schema.statistics where table_schema='apiplatform' and index_name='idx_bucket_route_time'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }
}
