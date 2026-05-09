package com.chat2api.backend.security;

import com.chat2api.backend.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        int limit = limitFor(request.getRequestURI());
        if (limit > 0 && !allowed(request, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail("RATE_LIMITED", "Too many requests"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private int limitFor(String uri) {
        if ("/api/auth/login".equals(uri)) {
            return securityProperties.authRateLimit();
        }
        if (uri != null && uri.startsWith("/api/reporter/")) {
            return securityProperties.reporterRateLimit();
        }
        return 0;
    }

    private boolean allowed(HttpServletRequest request, int limit) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - now % securityProperties.rateLimitWindow().toSeconds();
        String key = clientIp(request) + ":" + request.getMethod() + ":" + request.getRequestURI() + ":" + windowStart;
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(windowStart));
        cleanup(windowStart);
        return bucket.count().incrementAndGet() <= limit;
    }

    private void cleanup(long currentWindowStart) {
        buckets.entrySet().removeIf(entry -> entry.getValue().windowStart() < currentWindowStart - securityProperties.rateLimitWindow().toSeconds());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Bucket(long windowStart, AtomicInteger count) {
        private Bucket(long windowStart) {
            this(windowStart, new AtomicInteger());
        }
    }
}
