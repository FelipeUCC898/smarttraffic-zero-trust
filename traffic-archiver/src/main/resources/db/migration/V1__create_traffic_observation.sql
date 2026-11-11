CREATE TABLE traffic_observation (
    event_id UUID PRIMARY KEY,
    sensor_id VARCHAR(128) NOT NULL CHECK (length(trim(sensor_id)) > 0),
    zone_id VARCHAR(128) NOT NULL CHECK (length(trim(zone_id)) > 0),
    measured_at TIMESTAMPTZ NOT NULL,
    vehicle_count INTEGER NOT NULL CHECK (vehicle_count >= 0),
    average_speed_kmh NUMERIC NOT NULL CHECK (average_speed_kmh >= 0),
    congestion_state VARCHAR(16) NOT NULL
        CHECK (congestion_state IN ('DESPEJADA', 'MODERADA', 'CONGESTIONADA'))
);

CREATE INDEX traffic_observation_zone_measured_at_idx
    ON traffic_observation (zone_id, measured_at DESC, event_id DESC);
