package com.chat2api.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class SecurityProperties {
    private final String jwtSecret;
    private final Duration jwtTtl;
    private final List<String> corsAllowedOriginPatterns;
    private final long maxRequestBodyBytes;
    private final Duration rateLimitWindow;
    private final int authRateLimit;
    private final int reporterRateLimit;

    public SecurityProperties(
            @Value("${chat2api.security.jwt-secret}") String jwtSecret,
            @Value("${chat2api.security.jwt-ttl-seconds}") long jwtTtlSeconds,
            @Value("${chat2api.security.cors-allowed-origin-patterns}") String corsAllowedOriginPatterns,
            @Value("${chat2api.security.max-request-body-bytes}") long maxRequestBodyBytes,
            @Value("${chat2api.security.rate-limit-window-seconds}") long rateLimitWindowSeconds,
            @Value("${chat2api.security.auth-rate-limit}") int authRateLimit,
            @Value("${chat2api.security.reporter-rate-limit}") int reporterRateLimit
    ) {
        this.jwtSecret = jwtSecret;
        this.jwtTtl = Duration.ofSeconds(jwtTtlSeconds);
        this.corsAllowedOriginPatterns = Arrays.stream(corsAllowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        this.maxRequestBodyBytes = maxRequestBodyBytes;
        this.rateLimitWindow = Duration.ofSeconds(rateLimitWindowSeconds);
        this.authRateLimit = authRateLimit;
        this.reporterRateLimit = reporterRateLimit;
    }

    public String jwtSecret() {
        return jwtSecret;
    }

    public Duration jwtTtl() {
        return jwtTtl;
    }

    public List<String> corsAllowedOriginPatterns() {
        return corsAllowedOriginPatterns;
    }

    public long maxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public Duration rateLimitWindow() {
        return rateLimitWindow;
    }

    public int authRateLimit() {
        return authRateLimit;
    }

    public int reporterRateLimit() {
        return reporterRateLimit;
    }
}
