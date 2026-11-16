-- The deployment's explicit provisioning step creates the runtime role first.
-- Legacy/local databases without that role keep their existing authentication.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smarttraffic_archiver') THEN
        GRANT SELECT, INSERT ON TABLE public.traffic_observation TO smarttraffic_archiver;
    END IF;
END
$$;
