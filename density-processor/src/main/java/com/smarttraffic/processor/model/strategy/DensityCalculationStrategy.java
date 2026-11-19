package com.smarttraffic.processor.model.strategy;

import com.smarttraffic.processor.model.TrafficObservation;
import java.math.BigDecimal;

/**
 * Puerto de cálculo: la fórmula concreta y su unidad se acuerdan con el equipo.
 * Las implementaciones deben ser puras, sin publicar ni modificar estado externo.
 */
@FunctionalInterface
public interface DensityCalculationStrategy {
    BigDecimal calculate(TrafficObservation observation);
}
