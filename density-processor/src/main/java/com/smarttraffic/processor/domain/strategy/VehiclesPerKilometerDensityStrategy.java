package com.smarttraffic.processor.domain.strategy;

import com.smarttraffic.processor.domain.model.TrafficObservation;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

/** Densidad de la última observación: vehículos / longitud de zona en km. */
public final class VehiclesPerKilometerDensityStrategy implements DensityCalculationStrategy {
    private final Map<String, BigDecimal> zoneLengthKm;

    public VehiclesPerKilometerDensityStrategy(Map<String, BigDecimal> zoneLengthKm) {
        if (zoneLengthKm == null || zoneLengthKm.isEmpty()) {
            throw new IllegalArgumentException("Se requieren longitudes de zona en km");
        }
        zoneLengthKm.forEach((zone, length) -> {
            if (zone == null || zone.isBlank() || zone.length() > 128 || length == null || length.signum() <= 0) {
                throw new IllegalArgumentException("Cada zona requiere identidad válida y longitud positiva");
            }
        });
        this.zoneLengthKm = Map.copyOf(zoneLengthKm);
    }

    @Override
    public BigDecimal calculate(TrafficObservation observation) {
        if (observation == null) throw new IllegalArgumentException("observation es obligatoria");
        BigDecimal length = zoneLengthKm.get(observation.zoneId());
        if (length == null) throw new IllegalArgumentException("La zona no tiene longitud configurada");
        return BigDecimal.valueOf(observation.vehicleCount()).divide(length, MathContext.DECIMAL128);
    }
}
