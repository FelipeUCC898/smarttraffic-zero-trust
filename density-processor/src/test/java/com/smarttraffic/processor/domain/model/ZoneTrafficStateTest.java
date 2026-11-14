package com.smarttraffic.processor.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ZoneTrafficStateTest {
    private static final Instant MEASURED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void preservesStateAndVersionWithoutAssigningBusinessMeaning() {
        ZoneTrafficState state = new ZoneTrafficState("north", MEASURED_AT, new BigDecimal("42.50"),
                CongestionState.MODERADA, "version-pendiente");

        assertThat(state.zoneId()).isEqualTo("north");
        assertThat(state.measuredAt()).isEqualTo(MEASURED_AT);
        assertThat(state.density()).isEqualTo(new BigDecimal("42.50"));
        assertThat(state.congestionState()).isEqualTo(CongestionState.MODERADA);
        assertThat(state.version()).isEqualTo("version-pendiente");
    }

    @Test
    void acceptsZeroDensityAndTheCurrentIdentifierLimit() {
        ZoneTrafficState state = new ZoneTrafficState("z".repeat(128), MEASURED_AT, BigDecimal.ZERO,
                CongestionState.DESPEJADA, null);
        assertThat(state.density()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankZone(String zoneId) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficState(zoneId,
                MEASURED_AT, BigDecimal.ONE, CongestionState.MODERADA, null)).withMessageContaining("zoneId");
    }

    @Test
    void rejectsOversizedZone() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficState("z".repeat(129),
                MEASURED_AT, BigDecimal.ONE, CongestionState.MODERADA, null)).withMessageContaining("zoneId");
    }

    @Test
    void rejectsMissingTimeDensityOrCongestionAndNegativeDensity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficState("north", null,
                BigDecimal.ONE, CongestionState.MODERADA, null)).withMessageContaining("measuredAt");
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficState("north", MEASURED_AT,
                null, CongestionState.MODERADA, null)).withMessageContaining("density");
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficState("north", MEASURED_AT,
                new BigDecimal("-0.001"), CongestionState.MODERADA, null)).withMessageContaining("density");
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficState("north", MEASURED_AT,
                BigDecimal.ONE, null, null)).withMessageContaining("congestionState");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"v1", "2", "texto por acordar"})
    void transportsVersionWithoutAnUnapprovedConstraint(String version) {
        assertThat(new ZoneTrafficState("north", MEASURED_AT, BigDecimal.ONE,
                CongestionState.MODERADA, version).version()).isEqualTo(version);
    }
}
