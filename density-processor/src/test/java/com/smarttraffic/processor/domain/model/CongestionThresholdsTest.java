package com.smarttraffic.processor.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CongestionThresholdsTest {
    @ParameterizedTest
    @MethodSource("invalidThresholds")
    void rejectsMissingOrUnorderedThresholds(BigDecimal moderate, BigDecimal congested) {
        assertThatIllegalArgumentException().isThrownBy(() -> new CongestionThresholds(moderate, congested));
    }

    @Test
    void comparesNumericallyIncludingBoundaryValuesWithDifferentScales() {
        CongestionThresholds thresholds = new CongestionThresholds(new BigDecimal("0.5"), new BigDecimal("0.8"));

        assertThat(thresholds.reachesModerate(new BigDecimal("0.4999"))).isFalse();
        assertThat(thresholds.reachesModerate(new BigDecimal("0.500"))).isTrue();
        assertThat(thresholds.reachesCongested(new BigDecimal("0.7999"))).isFalse();
        assertThat(thresholds.reachesCongested(new BigDecimal("0.800"))).isTrue();
    }

    private static Stream<Arguments> invalidThresholds() {
        return Stream.of(
                Arguments.of(null, BigDecimal.TEN),
                Arguments.of(BigDecimal.ONE, null),
                Arguments.of(BigDecimal.ZERO, BigDecimal.TEN),
                Arguments.of(new BigDecimal("-1"), BigDecimal.TEN),
                Arguments.of(BigDecimal.TEN, BigDecimal.ONE),
                Arguments.of(BigDecimal.ONE, BigDecimal.ONE),
                Arguments.of(BigDecimal.ONE, BigDecimal.ZERO));
    }
}
