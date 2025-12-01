package com.backend.tpi.ms_solicitudes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para conectar con Keycloak Admin API
 */
@Configuration
@ConfigurationProperties(prefix = "keycloak.admin")
@Data
public class KeycloakAdminConfig {
    
    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
}
