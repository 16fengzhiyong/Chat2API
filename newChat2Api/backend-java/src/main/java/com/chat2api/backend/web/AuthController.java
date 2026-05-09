package com.chat2api.backend.web;

import com.chat2api.backend.security.JwtService;
import com.chat2api.backend.security.SecurityProperties;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    public AuthController(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, JwtService jwtService, SecurityProperties securityProperties) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        String username = String.valueOf(request.getOrDefault("username", ""));
        String password = String.valueOf(request.getOrDefault("password", ""));
        try {
            var user = userDetailsService.loadUserByUsername(username);
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new ResponseStatusException(UNAUTHORIZED, "Invalid username or password");
            }
            String token = jwtService.createToken(user.getUsername(), List.of("ADMIN"));
            return ApiResponse.ok(Map.of(
                    "token", token,
                    "tokenType", "Bearer",
                    "expiresAt", Instant.now().plus(securityProperties.jwtTtl()).toString(),
                    "user", Map.of("username", user.getUsername(), "roles", List.of("ADMIN"))
            ));
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid username or password");
        }
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(org.springframework.security.core.Authentication authentication) {
        return ApiResponse.ok(Map.of("username", authentication.getName(), "roles", authentication.getAuthorities().stream().map(Object::toString).toList()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.ok(Map.of("loggedOut", true));
    }
}
