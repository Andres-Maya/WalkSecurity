package com.andres.walksecurity.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "walksecurity.jwt")
public record JwtProperties(String secret, long expirationDays) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("walksecurity.jwt.secret debe tener al menos 32 caracteres");
        }
        if (expirationDays <= 0) expirationDays = 7;
    }
}
