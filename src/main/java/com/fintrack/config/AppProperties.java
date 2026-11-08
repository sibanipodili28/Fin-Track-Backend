package com.fintrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, String corsAllowedOrigins, boolean cookieSecure) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}
}
