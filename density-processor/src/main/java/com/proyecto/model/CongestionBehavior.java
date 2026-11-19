package com.proyecto.model;

import com.proyecto.model.CongestionState;
import com.proyecto.model.CongestionThresholds;
import java.math.BigDecimal;

/** Comportamiento State separado del enum que se utiliza en los contratos. */
public sealed interface CongestionBehavior
        permits ClearTrafficState, ModerateTrafficState, CongestedTrafficState {
    CongestionState label();

    CongestionBehavior transition(BigDecimal density, CongestionThresholds thresholds);
}
