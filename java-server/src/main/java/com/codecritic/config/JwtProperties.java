package com.codecritic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized JWT settings bound from the {@code codecritic.jwt} property prefix.
 *
 * <p>Controls the signing secret, token lifetime, issuer, and the request paths
 * that are reachable without authentication. Lombok generates the accessors and
 * mutators used by Spring Boot's configuration binder.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "codecritic.jwt")
public class JwtProperties {

    /**
     * Creates an empty properties object populated by Spring Boot's binder.
     */
    public JwtProperties() {
    }

    private String secret;
    private long expirationMs = 86400000L;
    private String issuer = "codecritic";
    private String[] permitAllPaths = {
            "/error",
            "/health",
            "/api/health",
            "/api/auth/login",
            "/api/auth/register",
            "/api/config"
    };
}
