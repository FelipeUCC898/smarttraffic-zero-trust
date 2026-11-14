package com.smarttraffic.processor.domain.state;

import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import java.math.BigDecimal;

public final class CongestedTrafficState implements CongestionBehavior {
    @Override
    public CongestionState label() {
        return CongestionState.CONGESTIONADA;
    }

    @Override
    public CongestionBehavior transition(BigDecimal density, CongestionThresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds es obligatorio");
        }
        if (!thresholds.reachesCongested(density)) {
            return new ModerateTrafficState().transition(density, thresholds);
        }
        return this;
    }
}
