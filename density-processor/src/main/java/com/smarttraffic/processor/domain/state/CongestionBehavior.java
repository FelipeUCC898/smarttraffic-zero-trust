package com.smarttraffic.processor.domain.state;

import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import java.math.BigDecimal;

/** Comportamiento State separado del enum que se utiliza en los contratos. */
public sealed interface CongestionBehavior
        permits ClearTrafficState, ModerateTrafficState, CongestedTrafficState {
    CongestionState label();

    CongestionBehavior transition(BigDecimal density, CongestionThresholds thresholds);
}
