package com.chat2api.backend.security;

import java.time.Instant;
import java.util.List;

public record JwtPrincipal(String username, List<String> roles, Instant expiresAt) {
}
