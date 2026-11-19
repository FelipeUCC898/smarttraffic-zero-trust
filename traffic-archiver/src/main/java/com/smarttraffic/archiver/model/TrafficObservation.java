package com.smarttraffic.archiver.model;

import com.smarttraffic.archiver.dto.TrafficObservationEvent;

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
public class TrafficObservation {
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

    private TrafficObservation(UUID eventId, String sensorId, String zoneId, Instant measuredAt,
            int vehicleCount, BigDecimal averageSpeedKmh,
            TrafficObservationEvent.CongestionState congestionState) {
        this.eventId = eventId;
        this.sensorId = sensorId;
        this.zoneId = zoneId;
        this.measuredAt = measuredAt;
        this.vehicleCount = vehicleCount;
        this.averageSpeedKmh = averageSpeedKmh;
        this.congestionState = congestionState;
    }

    public static TrafficObservation from(TrafficObservationEvent event, String kafkaKey) {
        if (event == null || event.eventId() == null || event.measuredAt() == null
                || event.vehicleCount() == null || event.vehicleCount() < 0
                || event.averageSpeedKmh() == null || event.averageSpeedKmh().signum() < 0
                || event.congestionState() == null || invalidIdentifier(event.sensorId())
                || invalidIdentifier(event.zoneId()) || !event.sensorId().equals(kafkaKey)) {
            throw new IllegalArgumentException("Invalid traffic observation or Kafka key");
        }
        return new TrafficObservation(event.eventId(), event.sensorId(), event.zoneId(), event.measuredAt(),
                event.vehicleCount(), event.averageSpeedKmh(), event.congestionState());
    }

    private static boolean invalidIdentifier(String value) {
        return value == null || value.isBlank() || value.length() > 128;
    }

    public UUID eventId() { return eventId; }
    public String sensorId() { return sensorId; }
    public String zoneId() { return zoneId; }
    public Instant measuredAt() { return measuredAt; }
    public int vehicleCount() {
        return vehicleCount;
    }
    public BigDecimal averageSpeedKmh() { return averageSpeedKmh; }
    public TrafficObservationEvent.CongestionState congestionState() { return congestionState; }
}
