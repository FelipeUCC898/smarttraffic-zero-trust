package com.smarttraffic.processor.infrastructure.security;

import com.rabbitmq.client.impl.CredentialsProvider;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.AmqpException;

/** JWT como contraseña SASL de RabbitMQ; renovación dirigida por el cliente nativo. */
public final class RabbitJwtCredentialsProvider implements CredentialsProvider {
    private final String clientId;
    private final ServiceAccessTokenProvider provider;
    private final Clock clock;
    private ServiceAccessToken current;

    public RabbitJwtCredentialsProvider(String clientId, ServiceAccessTokenProvider provider, Clock clock) {
        if (clientId == null || clientId.isBlank() || "guest".equals(clientId) || provider == null || clock == null)
            throw new IllegalArgumentException("Se requiere identidad de servicio, proveedor y reloj");
        this.clientId = clientId;
        this.provider = provider;
        this.clock = clock;
    }
    @Override public String getUsername() { return clientId; }
    @Override public synchronized String getPassword() { return token().value(); }
    @Override public synchronized Duration getTimeBeforeExpiration() { return Duration.between(clock.instant(), token().expiresAt()); }
    @Override public synchronized void refresh() {
        ServiceAccessToken replacement;
        try { replacement = provider.obtainToken(); }
        catch (RuntimeException exception) { throw new AmqpException("No se pudo renovar la credencial de servicio"); }
        if (replacement == null || !clientId.equals(replacement.principal()) || !replacement.expiresAt().isAfter(clock.instant()))
            throw new AmqpException("Credencial de servicio no válida para RabbitMQ");
        current = replacement;
    }
    private ServiceAccessToken token() {
        if (current == null || !current.expiresAt().isAfter(clock.instant())) refresh();
        return current;
    }
    @Override public String toString() { return "RabbitJwtCredentialsProvider[credentials=redacted]"; }
}
