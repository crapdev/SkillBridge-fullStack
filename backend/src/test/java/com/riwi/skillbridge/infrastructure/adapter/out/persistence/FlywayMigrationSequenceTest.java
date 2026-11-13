package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationSequenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @TempDir
    Path deployedMigrations;

    @Test
    void migratesAndValidatesAnEmptySchema() {
        Flyway flyway = flyway("fresh_install").load();

        flyway.migrate();
        flyway.validate();

        assertThat(flyway.info().applied())
            .filteredOn(migration -> migration.getVersion() != null)
            .extracting(migration -> migration.getVersion().getVersion())
            .containsExactlyElementsOf(IntStream.rangeClosed(1, 13).mapToObj(Integer::toString).toList());
    }

    @Test
    void renumberingPreservesDataAndValidatesTheExistingHistory() throws Exception {
        try (var migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).toList()) {
                String[] name = migration.getFileName().toString().split("__", 2);
                int version = Integer.parseInt(name[0].substring(1));
                int oldVersion = version <= 2 ? version : version == 13 ? 20 : version + 1;
                Files.copy(migration, deployedMigrations.resolve("V" + oldVersion + "__" + name[1]));
            }
        }
        Flyway previous = flyway("public").locations("filesystem:" + deployedMigrations).load();
        previous.migrate();
        previous.validate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("UPDATE public.offerings SET title = ? WHERE id = ?::uuid",
            "Mentoría actualizada", "11111111-1111-1111-1111-111111111111");
        String historyQuery = """
            SELECT installed_rank, description, type, checksum, installed_by, installed_on, execution_time, success
            FROM public.flyway_schema_history
            ORDER BY installed_rank
            """;
        var previousHistory = jdbc.queryForList(historyQuery);

        String script = "/tmp/bug-13-renumber-flyway-history.sql";
        POSTGRES.copyFileToContainer(
            MountableFile.forHostPath(Path.of("../scripts/bug-13-renumber-flyway-history.sql")), script);
        var result = POSTGRES.execInContainer("psql", "-v", "ON_ERROR_STOP=1",
            "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(), "-f", script);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        Flyway normalStartup = flyway("public").load();
        normalStartup.validate();
        assertThat(normalStartup.migrate().migrationsExecuted).isZero();
        assertThat(normalStartup.info().current().getVersion().getVersion()).isEqualTo("13");

        assertThat(jdbc.queryForList(historyQuery)).isEqualTo(previousHistory);
        assertThat(jdbc.queryForObject(
            "SELECT title FROM public.offerings WHERE id = ?::uuid", String.class,
            "11111111-1111-1111-1111-111111111111")).isEqualTo("Mentoría actualizada");
    }

    private FluentConfiguration flyway(String schema) {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema);
    }
}
