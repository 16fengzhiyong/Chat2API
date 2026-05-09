package com.chat2api.backend.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;

    public JwtService(ObjectMapper objectMapper, SecurityProperties securityProperties) {
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }

    public String createToken(String username, List<String> roles) {
        Instant now = Instant.now();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("roles", roles);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(securityProperties.jwtTtl()).getEpochSecond());
        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return unsigned + "." + sign(unsigned);
    }

    public Optional<JwtPrincipal> verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(unsigned).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), MAP_TYPE);
            long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                return Optional.empty();
            }
            String subject = String.valueOf(payload.get("sub"));
            Object rolesValue = payload.get("roles");
            List<String> roles = rolesValue instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
            return Optional.of(new JwtPrincipal(subject, roles, Instant.ofEpochSecond(exp)));
        } catch (Exception error) {
            return Optional.empty();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception error) {
            throw new IllegalStateException("Failed to encode token", error);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(securityProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Failed to sign token", error);
        }
    }
}
