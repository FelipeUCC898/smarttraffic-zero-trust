package com.smarttraffic.probe;

import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.sql.DriverManager;
import javax.security.auth.callback.*;
import javax.security.auth.login.AppConfigurationEntry;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.security.auth.*;
import org.apache.kafka.common.security.oauthbearer.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real broker tests, executed only by the isolated Docker harness. */
class BrokerSecurityTest {
    static String currentToken;
    static PrivateKey key;
    @BeforeAll static void loadKey() throws Exception {
        String pem = Files.readString(Path.of("/fixtures/jwt-private.pem"))
            .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }
    static String token(String subject, String audience, String issuer, Instant expiry, PrivateKey signingKey) {
        return Jwts.builder().header().keyId("smarttraffic-rs256-v1").and().subject(subject)
            .issuer(issuer).audience().add(audience).and().issuedAt(Date.from(Instant.now().minusSeconds(120)))
            .expiration(Date.from(expiry)).claim("scope", "test")
            .signWith(signingKey, Jwts.SIG.RS256).compact();
    }
    static String valid(String subject) { return token(subject,"smarttraffic-brokers","smarttraffic-auth",Instant.now().plusSeconds(300),key); }
    static Properties config(String host, boolean trust) {
        Properties p = new Properties();
        p.put("bootstrap.servers",host+":9093"); p.put("security.protocol","SASL_SSL");
        p.put("sasl.mechanism","OAUTHBEARER");
        p.put("sasl.jaas.config","org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;");
        p.put("sasl.login.callback.handler.class", Callback.class.getName());
        p.put("ssl.endpoint.identification.algorithm","https");
        if(trust) { p.put("ssl.truststore.location","/fixtures/truststore.p12"); p.put("ssl.truststore.type","PKCS12"); p.put("ssl.truststore.password",System.getenv("TRUSTSTORE_PASSWORD")); }
        p.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
        p.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");
        p.put("max.block.ms",30000); p.put("request.timeout.ms",10000); p.put("delivery.timeout.ms",30000);
        p.put("retries",0); p.put("enable.idempotence",false);
        return p;
    }
    static void send(String jwt, String topic, String host, boolean trust) throws Exception {
        currentToken=jwt;
        try(var producer = new KafkaProducer<String,String>(config(host,trust))) {
            String observation="{\"eventId\":\""+UUID.randomUUID()+"\",\"sensorId\":\"security-probe\",\"zoneId\":\"north\",\"measuredAt\":\""+Instant.now()+"\",\"vehicleCount\":0,\"averageSpeedKmh\":0,\"congestionState\":\"DESPEJADA\"}";
            producer.send(new ProducerRecord<>(topic,"security-probe",observation)).get(45,TimeUnit.SECONDS);
        }
    }
    static void rejects(Class<? extends Throwable> expected, String jwt, String topic, String host, boolean trust) {
        Exception failure=assertThrows(Exception.class,()->send(jwt,topic,host,trust));
        for(Throwable t=failure;t!=null;t=t.getCause()) if(expected.isInstance(t)) return;
        fail("Expected "+expected.getSimpleName()+", got "+failure, failure);
    }
    @Test void simulatorCanPublish() throws Exception { send(valid("traffic-simulator"),"raw_traffic_data","kafka",true); }
    @Test void simulatorCannotWriteAnotherTopic() { rejects(TopicAuthorizationException.class,valid("traffic-simulator"),"forbidden_topic","kafka",true); }
    @Test void dashboardCannotPublishTraffic() { rejects(TopicAuthorizationException.class,valid("traffic-dashboard"),"raw_traffic_data","kafka",true); }
    @Test void rejectsWrongAudience() { rejects(SaslAuthenticationException.class,token("traffic-simulator","wrong","smarttraffic-auth",Instant.now().plusSeconds(300),key),"raw_traffic_data","kafka",true); }
    @Test void rejectsWrongIssuer() { rejects(SaslAuthenticationException.class,token("traffic-simulator","smarttraffic-brokers","wrong",Instant.now().plusSeconds(300),key),"raw_traffic_data","kafka",true); }
    @Test void rejectsExpiredToken() { rejects(SaslAuthenticationException.class,token("traffic-simulator","smarttraffic-brokers","smarttraffic-auth",Instant.now().minusSeconds(60),key),"raw_traffic_data","kafka",true); }
    @Test void rejectsForgedSignature() throws Exception { var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); rejects(SaslAuthenticationException.class,token("traffic-simulator","smarttraffic-brokers","smarttraffic-auth",Instant.now().plusSeconds(300),generator.generateKeyPair().getPrivate()),"raw_traffic_data","kafka",true); }
    @Test void rejectsUntrustedCa() { rejects(SslAuthenticationException.class,valid("traffic-simulator"),"raw_traffic_data","kafka",false); }
    @Test void rejectsWrongHostname() throws Exception { rejects(SslAuthenticationException.class,valid("traffic-simulator"),"raw_traffic_data",java.net.InetAddress.getByName("kafka").getHostAddress(),true); }
    @Test void postgresRequiresVerifiedTls() throws Exception {
        Properties p=new Properties(); p.put("user","smarttraffic");p.put("password",System.getenv("POSTGRES_PASSWORD"));
        assertThrows(java.sql.SQLException.class,()->DriverManager.getConnection("jdbc:postgresql://postgres/smarttraffic?sslmode=disable",p));
        try(var c=DriverManager.getConnection("jdbc:postgresql://postgres/smarttraffic?sslmode=verify-full&sslrootcert=/fixtures/ca.pem",p);
            var s=c.createStatement(); var r=s.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()")) { assertTrue(r.next()); assertTrue(r.getBoolean(1)); }
        String ip=java.net.InetAddress.getByName("postgres").getHostAddress();
        assertThrows(java.sql.SQLException.class,()->DriverManager.getConnection("jdbc:postgresql://"+ip+"/smarttraffic?sslmode=verify-full&sslrootcert=/fixtures/ca.pem",p));
    }
    public static class Callback implements AuthenticateCallbackHandler {
        public void configure(Map<String,?> c,String m,List<AppConfigurationEntry> e) { }
        public void close() { }
        public void handle(javax.security.auth.callback.Callback[] callbacks) throws UnsupportedCallbackException {
            for(var c:callbacks) {
                if(c instanceof OAuthBearerTokenCallback t) {
                    final String value=currentToken;
                    t.token(new OAuthBearerToken() {
                        public String value(){return value;} public Set<String> scope(){return Set.of("test");}
                        public String principalName(){return "test";} public long lifetimeMs(){return Instant.now().plusSeconds(300).toEpochMilli();}
                        public Long startTimeMs(){return Instant.now().minusSeconds(120).toEpochMilli();}
                    });
                } else if(c instanceof SaslExtensionsCallback e) e.extensions(new SaslExtensions(Map.of()));
                else throw new UnsupportedCallbackException(c);
            }
        }
    }
}
