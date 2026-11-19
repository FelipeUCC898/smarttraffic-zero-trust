package com.proyecto.config.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic.auth")
public class AuthClientProperties {
    private URI tokenEndpoint; private String clientId; private String clientSecret; private long timeoutMs=5000;
    private String issuer; private String audience; private String publicKey;
    private String caCertificate;
    public String caCertificate(){return caCertificate;}
    public void setCaCertificate(String value){caCertificate=value;}
    public URI tokenEndpoint(){return tokenEndpoint;} public String clientId(){return clientId;} public String clientSecret(){return clientSecret;} public long timeoutMs(){return timeoutMs;} public String issuer(){return issuer;} public String audience(){return audience;} public String publicKey(){return publicKey;}
    public void setTokenEndpoint(URI v){tokenEndpoint=v;} public void setClientId(String v){clientId=v;} public void setClientSecret(String v){clientSecret=v;} public void setTimeoutMs(long v){timeoutMs=v;} public void setIssuer(String v){issuer=v;} public void setAudience(String v){audience=v;} public void setPublicKey(String v){publicKey=v;}
    public void validate(){ if(tokenEndpoint==null || !"https".equalsIgnoreCase(tokenEndpoint.getScheme()) || tokenEndpoint.getHost()==null || tokenEndpoint.getUserInfo()!=null || clientId==null || clientId.isBlank() || clientSecret==null || clientSecret.isBlank() || timeoutMs<=0 || issuer==null || issuer.isBlank() || audience==null || audience.isBlank() || publicKey==null || publicKey.isBlank()) throw new IllegalArgumentException("Configuración OAuth2 HTTPS incompleta"); }
}
