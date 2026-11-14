package com.smarttraffic.processor.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CongestionTransitionsTest {
    // Datos artificiales de prueba; no son los umbrales oficiales de SmartTraffic.
    private static final CongestionThresholds THRESHOLDS =
            new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20"));

    @ParameterizedTest(name = "{0} con densidad {1} pasa a {2}")
    @MethodSource("transitions")
    void transitionsFromEveryStateIncludingThresholdBoundaries(CongestionBehavior initial,
            BigDecimal density, CongestionState expected) {
        CongestionBehavior next = initial.transition(density, THRESHOLDS);

        assertThat(next.label()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("states")
    void rejectsMissingOrNegativeDensityAndMissingThresholds(CongestionBehavior initial) {
        assertThatIllegalArgumentException().isThrownBy(() -> initial.transition(null, THRESHOLDS))
                .withMessageContaining("density");
        assertThatIllegalArgumentException().isThrownBy(() -> initial.transition(new BigDecimal("-0.01"), THRESHOLDS))
                .withMessageContaining("density");
        assertThatIllegalArgumentException().isThrownBy(() -> initial.transition(BigDecimal.ONE, null))
                .withMessageContaining("thresholds");
    }

    @ParameterizedTest
    @MethodSource("states")
    void leavesTheOriginalBehaviorUnchanged(CongestionBehavior initial) {
        CongestionState original = initial.label();

        initial.transition(BigDecimal.ZERO, THRESHOLDS);
        initial.transition(new BigDecimal("100"), THRESHOLDS);

        assertThat(initial.label()).isEqualTo(original);
    }

    private static Stream<CongestionBehavior> states() {
        return Stream.of(new ClearTrafficState(), new ModerateTrafficState(), new CongestedTrafficState());
    }

    private static Stream<Arguments> transitions() {
        return states().flatMap(state -> Stream.of(
                Arguments.of(state, BigDecimal.ZERO, CongestionState.DESPEJADA),
                Arguments.of(state, new BigDecimal("9.999"), CongestionState.DESPEJADA),
                Arguments.of(state, new BigDecimal("10.00"), CongestionState.MODERADA),
                Arguments.of(state, new BigDecimal("19.999"), CongestionState.MODERADA),
                Arguments.of(state, new BigDecimal("20.00"), CongestionState.CONGESTIONADA),
                Arguments.of(state, new BigDecimal("100"), CongestionState.CONGESTIONADA)));
    }
}
