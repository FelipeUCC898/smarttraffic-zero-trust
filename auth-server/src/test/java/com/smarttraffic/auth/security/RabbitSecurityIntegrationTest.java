package com.smarttraffic.auth.security;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SMARTTRAFFIC_RABBIT_TEST_DIR", matches=".+")
class RabbitSecurityIntegrationTest {
    private final Path directory=Path.of(System.getenv("SMARTTRAFFIC_RABBIT_TEST_DIR"));

    private String token(String client, String audience, long seconds) {
        return new JwtService("", "rs256",directory.resolve("jwt-private.pem").toString(),seconds,"smarttraffic-auth",audience).issue(client);
    }

    private Connection connect(String jwt, String host, boolean trusted) throws Exception {
        var factory=new ConnectionFactory();
        factory.setHost(host); factory.setPort(15671); factory.setVirtualHost("/smarttraffic");
        factory.setUsername("service"); factory.setPassword(jwt);
        factory.setConnectionTimeout(5000); factory.setHandshakeTimeout(5000); factory.setAutomaticRecoveryEnabled(false);
        var store=KeyStore.getInstance(KeyStore.getDefaultType()); store.load(null,null);
        if(trusted) try(var input=Files.newInputStream(directory.resolve("server.pem"))) {
            store.setCertificateEntry("test-ca",CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        var context=SSLContext.getInstance("TLSv1.2"); context.init(null,trust.getTrustManagers(),null);
        factory.useSslProtocol(context); factory.enableHostnameVerification();
        return factory.newConnection();
    }

    @Test void processorCanDeclareUpdatesButNotUnrelatedResources() throws Exception {
        try(var connection=connect(token("processor","smarttraffic-brokers",900),"localhost",true)) {
            try(var channel=connection.createChannel()) { channel.exchangeDeclare("traffic_updates","fanout",true); }
            var denied=connection.createChannel();
            assertThrows(Exception.class,()->denied.exchangeDeclare("forbidden","fanout",true));
        }
    }
    @Test void dashboardCannotDeclareQueryExchange() throws Exception {
        try(var connection=connect(token("traffic-dashboard","smarttraffic-brokers",900),"localhost",true)) {
            var channel=connection.createChannel();
            assertThrows(Exception.class,()->channel.exchangeDeclare("query_traffic","direct",true));
        }
    }
    @Test void rejectsWrongAudience() { assertThrows(Exception.class,()->connect(token("processor","wrong",900),"localhost",true)); }
    @Test void rejectsExpiredJwt() { assertThrows(Exception.class,()->connect(token("processor","smarttraffic-brokers",-60),"localhost",true)); }
    @Test void rejectsWrongHostname() { assertThrows(Exception.class,()->connect(token("processor","smarttraffic-brokers",900),"127.0.0.1",true)); }
    @Test void rejectsUntrustedCertificate() { assertThrows(Exception.class,()->connect(token("processor","smarttraffic-brokers",900),"localhost",false)); }
    @Test void rejectsGuestPasswordAndTamperedSignature() {
        assertThrows(Exception.class,()->connect("guest","localhost",true));
        String jwt=token("processor","smarttraffic-brokers",900);
        int offset=jwt.lastIndexOf('.')+10;
        String tampered=jwt.substring(0,offset)+(jwt.charAt(offset)=='A'?'B':'A')+jwt.substring(offset+1);
        assertThrows(Exception.class,()->connect(tampered,"localhost",true));
    }
    @Test void routesAnswersToOnlyTheRequestingReplica() throws Exception {
        try(var a=connect(token("query-client","smarttraffic-brokers",900),"localhost",true);
            var b=connect(token("query-client","smarttraffic-brokers",900),"localhost",true);
            var dispatcher=connect(token("alert-dispatcher","smarttraffic-brokers",900),"localhost",true);
            var ac=a.createChannel(); var bc=b.createChannel(); var dc=dispatcher.createChannel()) {
            dc.exchangeDeclare("query_traffic","direct",true);
            dc.exchangeDeclare("query_answers","topic",true);
            dc.queueDeclare("query_traffic_queue",true,false,false,null);
            dc.queueBind("query_traffic_queue","query_traffic","query-client.query");
            String qa="query_answers.query-client."+java.util.UUID.randomUUID();
            String qb="query_answers.query-client."+java.util.UUID.randomUUID();
            ac.queueDeclare(qa,false,true,true,null); ac.queueBind(qa,"query_answers",qa);
            bc.queueDeclare(qb,false,true,true,null); bc.queueBind(qb,"query_answers",qb);
            String id=java.util.UUID.randomUUID().toString();
            ac.confirmSelect(); ac.basicPublish("query_traffic","query-client.query",new com.rabbitmq.client.AMQP.BasicProperties.Builder().correlationId(id).replyTo(qa).build(),"request".getBytes()); ac.waitForConfirmsOrDie(5000);
            var request=awaitMessage(dc,"query_traffic_queue"); assertNotNull(request);
            assertEquals(id,request.getProps().getCorrelationId());
            dc.confirmSelect(); dc.basicPublish("query_answers",request.getProps().getReplyTo(),new com.rabbitmq.client.AMQP.BasicProperties.Builder().correlationId(id).build(),"answer".getBytes()); dc.waitForConfirmsOrDie(5000);
            assertEquals(id,awaitMessage(ac,qa).getProps().getCorrelationId());
            assertNull(bc.basicGet(qb,true));
        }
    }
    private com.rabbitmq.client.GetResponse awaitMessage(com.rabbitmq.client.Channel channel,String queue) throws Exception {
        for(int i=0;i<40;i++) { var response=channel.basicGet(queue,true); if(response!=null) return response; Thread.sleep(50); }
        fail("No message received from "+queue); return null;
    }
}
