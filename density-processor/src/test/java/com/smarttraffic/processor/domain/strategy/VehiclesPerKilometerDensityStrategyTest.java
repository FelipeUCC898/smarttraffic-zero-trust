package com.smarttraffic.processor.domain.strategy;

import static org.assertj.core.api.Assertions.*;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VehiclesPerKilometerDensityStrategyTest {
    @Test void dividesVehiclesByConfiguredKilometersWithoutNormalizingZones() {
        var strategy = new VehiclesPerKilometerDensityStrategy(Map.of("north", new BigDecimal("2")));
        assertThat(strategy.calculate(reading("north", 42))).isEqualByComparingTo("21");
        assertThat(strategy.calculate(reading("north", 0))).isEqualByComparingTo("0");
        assertThatIllegalArgumentException().isThrownBy(() -> strategy.calculate(reading(" north", 42)));
    }
    @Test void usesDecimal128ForNonTerminatingDivision() {
        var strategy = new VehiclesPerKilometerDensityStrategy(Map.of("north", new BigDecimal("3")));
        assertThat(strategy.calculate(reading("north", 1))).isEqualByComparingTo("0.3333333333333333333333333333333333");
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1"})
    void refusesNonPositiveLength(String length) {
        assertThatIllegalArgumentException().isThrownBy(() -> new VehiclesPerKilometerDensityStrategy(Map.of("north", new BigDecimal(length))));
    }
    @Test void rejectsMissingMapMissingZoneAndMissingObservation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VehiclesPerKilometerDensityStrategy(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new VehiclesPerKilometerDensityStrategy(Map.of()));
        var strategy = new VehiclesPerKilometerDensityStrategy(Map.of("north", BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> strategy.calculate(null));
        assertThatIllegalArgumentException().isThrownBy(() -> strategy.calculate(reading("south", 42)));
    }
    @Test void copiesConfigurationInsteadOfRetainingMutableMaps() {
        var lengths = new HashMap<String, BigDecimal>(); lengths.put("north", new BigDecimal("2"));
        var strategy = new VehiclesPerKilometerDensityStrategy(lengths);
        lengths.put("north", BigDecimal.ONE);
        assertThat(strategy.calculate(reading("north", 42))).isEqualByComparingTo("21");
    }
    private TrafficObservation reading(String zone, int count) {
        return new TrafficObservation(new UUID(0, 1), "sensor-1", zone, Instant.parse("2026-01-01T00:00:00Z"),
                count, BigDecimal.TEN, CongestionState.MODERADA);
    }
}
