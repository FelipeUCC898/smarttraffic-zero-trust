package com.smarttraffic.archiver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "traffic_observation")
class TrafficObservation {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "sensor_id", nullable = false, length = 128)
    private String sensorId;

    @Column(name = "zone_id", nullable = false, length = 128)
    private String zoneId;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "vehicle_count", nullable = false)
    private int vehicleCount;

    @Column(name = "average_speed_kmh", nullable = false)
    private BigDecimal averageSpeedKmh;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_state", nullable = false, length = 16)
    private TrafficObservationEvent.CongestionState congestionState;

    protected TrafficObservation() {
    }

    int vehicleCount() {
        return vehicleCount;
    }
}
