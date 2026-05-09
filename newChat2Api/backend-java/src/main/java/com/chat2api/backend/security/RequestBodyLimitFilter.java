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

@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public RequestBodyLimitFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > securityProperties.maxRequestBodyBytes()) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail("REQUEST_TOO_LARGE", "Request body is too large"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
