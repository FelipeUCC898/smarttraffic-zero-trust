package com.smarttraffic.processor.model.state;

import com.smarttraffic.processor.model.CongestionState;
import com.smarttraffic.processor.model.CongestionThresholds;
import java.math.BigDecimal;

/** Comportamiento State separado del enum que se utiliza en los contratos. */
public sealed interface CongestionBehavior
        permits ClearTrafficState, ModerateTrafficState, CongestedTrafficState {
    CongestionState label();

    CongestionBehavior transition(BigDecimal density, CongestionThresholds thresholds);
}
