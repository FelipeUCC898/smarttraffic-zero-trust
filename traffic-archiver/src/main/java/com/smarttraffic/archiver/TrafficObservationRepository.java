package com.smarttraffic.archiver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface TrafficObservationRepository extends JpaRepository<TrafficObservation, UUID> {
    Optional<TrafficObservation> findFirstByZoneIdOrderByMeasuredAtDescEventIdDesc(String zoneId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO traffic_observation
                (event_id, sensor_id, zone_id, measured_at, vehicle_count, average_speed_kmh, congestion_state)
            VALUES (:eventId, :sensorId, :zoneId, :measuredAt, :vehicleCount, :averageSpeedKmh, :congestionState)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int archive(@Param("eventId") UUID eventId, @Param("sensorId") String sensorId,
                @Param("zoneId") String zoneId, @Param("measuredAt") Instant measuredAt,
                @Param("vehicleCount") int vehicleCount, @Param("averageSpeedKmh") BigDecimal averageSpeedKmh,
                @Param("congestionState") String congestionState);
}
