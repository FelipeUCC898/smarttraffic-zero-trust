package com.smarttraffic.probe;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SMARTTRAFFIC_POSTGRES_ROLES", matches="true")
class PostgresRuntimeTest {
    private Connection connect(String database, String user, String password) throws SQLException {
        Properties p = new Properties(); p.put("user", user); p.put("password", password);
        return DriverManager.getConnection("jdbc:postgresql://postgres/" + database +
            "?sslmode=verify-full&sslrootcert=/fixtures/ca.pem", p);
    }
    private void restrictedRuntime(String database) throws Exception {
        try (var db = connect(database, "smarttraffic_archiver", System.getenv("ARCHIVER_DB_PASSWORD")); var sql = db.createStatement()) {
            try (var result = sql.executeQuery("SELECT current_user, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname=current_user")) {
                assertTrue(result.next()); assertEquals("smarttraffic_archiver", result.getString(1));
                assertFalse(result.getBoolean(2)); assertFalse(result.getBoolean(3)); assertFalse(result.getBoolean(4));
            }
            try (var result = sql.executeQuery("SELECT has_schema_privilege(current_user,'public','CREATE'), has_table_privilege(current_user,'traffic_observation','UPDATE'), has_table_privilege(current_user,'traffic_observation','DELETE')")) {
                result.next(); assertFalse(result.getBoolean(1)); assertFalse(result.getBoolean(2)); assertFalse(result.getBoolean(3));
            }
            SQLException failure = assertThrows(SQLException.class, () -> sql.execute("CREATE TABLE public.probe_forbidden(id integer)"));
            assertEquals("42501", failure.getSQLState());
            sql.executeQuery("SELECT count(*) FROM traffic_observation").close();
            db.setAutoCommit(false);
            try (var insert = db.prepareStatement("INSERT INTO traffic_observation VALUES (?, 'runtime-probe', 'north', now(), 1, 1, 'DESPEJADA')")) {
                insert.setObject(1, UUID.randomUUID()); assertEquals(1, insert.executeUpdate());
            } finally { db.rollback(); }
        }
    }
    @Test void existingDatabaseAllowsDmlAndDeniesDdl() throws Exception {
        restrictedRuntime("smarttraffic");
    }
    @Test void migrationAndRuntimeWorkOnFreshSchema() throws Exception {
        // Dedicated new database is deliberately retained; never drop user data.
        String database = "probe_roles_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = connect("smarttraffic", "smarttraffic", System.getenv("POSTGRES_PASSWORD")); var sql = admin.createStatement()) {
            sql.execute("CREATE DATABASE " + database);
        }
        try (var admin = connect(database, "smarttraffic", System.getenv("POSTGRES_PASSWORD")); var sql = admin.createStatement()) {
            sql.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            sql.execute("ALTER SCHEMA public OWNER TO smarttraffic_migrator");
            sql.execute("GRANT USAGE ON SCHEMA public TO smarttraffic_archiver");
        }
        try (var migrator = connect(database, "smarttraffic_migrator", System.getenv("MIGRATOR_DB_PASSWORD")); var sql = migrator.createStatement()) {
            for (String file : List.of("V1__create_traffic_observation.sql", "V2__grant_archiver_runtime.sql")) {
                sql.execute(Files.readString(Path.of("/workspace/traffic-archiver/src/main/resources/db/migration", file)));
            }
        }
        restrictedRuntime(database);
        System.out.println("PASS: fresh schema migrations and runtime restrictions; retained local database " + database);
    }
}
