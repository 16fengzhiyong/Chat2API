package com.chat2api.backend.config;

import com.chat2api.backend.security.JwtAuthenticationFilter;
import com.chat2api.backend.security.RateLimitFilter;
import com.chat2api.backend.security.RequestBodyLimitFilter;
import com.chat2api.backend.security.SecurityProperties;
import com.chat2api.backend.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter, RequestBodyLimitFilter requestBodyLimitFilter, ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.cors(Customizer.withDefaults());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/health", "/actuator/health", "/v1/**").permitAll()
                .requestMatchers("/api/auth/login", "/api/reporter/**").permitAll()
                .anyRequest().authenticated()
        );
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, error) -> writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"))
                .accessDeniedHandler((request, response, error) -> writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied"))
        );
        http.httpBasic(basic -> basic.disable());
        http.formLogin(form -> form.disable());
        http.logout(logout -> logout.disable());
        http.headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .cacheControl(Customizer.withDefaults())
        );
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(requestBodyLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${chat2api.admin.username}") String username,
            @Value("${chat2api.admin.password}") String password,
            PasswordEncoder passwordEncoder
    ) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties securityProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(securityProperties.corsAllowedOriginPatterns().isEmpty() ? List.of("http://localhost:*", "http://127.0.0.1:*") : securityProperties.corsAllowedOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Reporter-Id", "X-Reporter-Secret"));
        configuration.setExposedHeaders(List.of("Content-Type"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(HttpServletResponse response, ObjectMapper objectMapper, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(code, message));
    }
}
