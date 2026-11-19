package com.proyecto.service;

import com.proyecto.model.ServiceAccessToken;
import com.rabbitmq.client.impl.CredentialsProvider;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.AmqpException;

public final class RabbitJwtCredentialsProvider implements CredentialsProvider {
    private final String clientId; private final ServiceAccessTokenProvider provider; private final Clock clock; private ServiceAccessToken current;
    public RabbitJwtCredentialsProvider(String clientId, ServiceAccessTokenProvider provider, Clock clock) { this.clientId=clientId; this.provider=provider; this.clock=clock; }
    @Override public String getUsername() { return clientId; }
    @Override public synchronized String getPassword() { return token().value(); }
    @Override public synchronized Duration getTimeBeforeExpiration() { return Duration.between(clock.instant(), token().expiresAt()); }
    @Override public synchronized void refresh() { try { current=provider.obtainToken(); } catch (RuntimeException e) { throw new AmqpException("No se pudo renovar OAuth2 RabbitMQ"); } if (current==null || !clientId.equals(current.principal()) || !current.expiresAt().isAfter(clock.instant())) throw new AmqpException("JWT RabbitMQ inválido"); }
    private ServiceAccessToken token() { if (current==null || !current.expiresAt().isAfter(clock.instant())) refresh(); return current; }
    @Override public String toString() { return "RabbitJwtCredentialsProvider[credentials=redacted]"; }
}
