package com.proyecto.model;

import com.proyecto.model.CongestionState;
import com.proyecto.model.CongestionThresholds;
import java.math.BigDecimal;

public final class ModerateTrafficState implements CongestionBehavior {
    @Override
    public CongestionState label() {
        return CongestionState.MODERADA;
    }

    @Override
    public CongestionBehavior transition(BigDecimal density, CongestionThresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds es obligatorio");
        }
        if (!thresholds.reachesModerate(density)) {
            return new ClearTrafficState();
        }
        if (thresholds.reachesCongested(density)) {
            return new CongestedTrafficState();
        }
        return this;
    }
}
