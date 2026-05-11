package com.chat2api.backend.security;

import com.chat2api.backend.repository.AdminUserRepository;
import com.chat2api.backend.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(AdminUserRepository adminUserRepository, ObjectMapper objectMapper) {
        this.adminUserRepository = adminUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && shouldBlock(request, authentication.getName())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail("PASSWORD_CHANGE_REQUIRED", "Password change required"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldBlock(HttpServletRequest request, String username) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.equals("/api/auth/me") || path.equals("/api/auth/change-password") || path.equals("/api/auth/logout")) {
            return false;
        }
        return adminUserRepository.findByUsernameAndEnabledTrue(username).map(user -> user.isMustChangePassword()).orElse(false);
    }
}
