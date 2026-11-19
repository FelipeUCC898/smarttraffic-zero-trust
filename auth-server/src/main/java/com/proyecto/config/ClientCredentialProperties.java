package com.proyecto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "security.clients")
public class ClientCredentialProperties {
    private Map<String, String> secrets = Map.of();

    public Map<String, String> getSecrets() {
        return secrets;
    }

    public void setSecrets(Map<String, String> secrets) {
        this.secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }
}
