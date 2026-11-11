package com.smarttraffic.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "SMARTTRAFFIC_POSTGRES_TEST", matches = "true")
class PostgreSqlConnectionTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Value("${POSTGRES_DB:smarttraffic}")
    private String databaseName;

    @Autowired
    private TrafficObservationRepository repository;

    @Test
    void connectsToTheConfiguredPostgreSqlDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo(databaseName);
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL");
    }

    @Test
    @Transactional
    void archivesOnceAndFindsTheNewestObservationForAZone() {
        String zone = UUID.randomUUID().toString();
        UUID eventId = UUID.randomUUID();
        Instant measuredAt = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(repository.archive(eventId, "sensor-1", zone, measuredAt, 42, BigDecimal.TEN, "MODERADA")).isEqualTo(1);
        assertThat(repository.archive(eventId, "sensor-1", zone, measuredAt, 99, BigDecimal.TEN, "DESPEJADA")).isZero();
        repository.archive(UUID.randomUUID(), "sensor-1", zone, measuredAt.minusSeconds(1), 4, BigDecimal.TEN, "DESPEJADA");

        assertThat(repository.findFirstByZoneIdOrderByMeasuredAtDescEventIdDesc(zone))
                .hasValueSatisfying(state -> assertThat(state.vehicleCount()).isEqualTo(42));
    }
}
