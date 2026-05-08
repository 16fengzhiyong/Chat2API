package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@Order(0)
public class BuiltinProviderForwarder extends BaseProviderForwarder {
    private static final Set<String> SUPPORTED = Set.of("deepseek", "glm", "kimi", "qwen", "qwen-ai", "zai", "minimax", "mimo", "perplexity");

    public BuiltinProviderForwarder(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public boolean supports(String vendor) {
        return SUPPORTED.contains(vendor);
    }

    @Override
    protected HttpHeaders buildHeaders(ProviderEntity provider, Map<String, String> credentials) {
        HttpHeaders headers = super.buildHeaders(provider, credentials);
        switch (provider.getVendor()) {
            case "qwen-ai", "mimo", "perplexity" -> {
                String cookie = first(credentials, "cookie", "cookies");
                if (cookie != null) {
                    headers.set(HttpHeaders.COOKIE, cookie);
                }
            }
            case "glm" -> {
                String refreshToken = first(credentials, "refresh_token", "refreshToken", "token");
                if (refreshToken != null) {
                    headers.setBearerAuth(refreshToken.replaceFirst("(?i)^Bearer\\s+", ""));
                }
            }
            case "minimax" -> {
                String token = first(credentials, "jwt", "token", "accessToken");
                String realUserId = first(credentials, "realUserID", "realUserId", "userId");
                if (token != null) {
                    headers.setBearerAuth(token.replaceFirst("(?i)^Bearer\\s+", ""));
                }
                if (realUserId != null) {
                    headers.set("X-User-Id", realUserId);
                }
            }
            default -> {
            }
        }
        return headers;
    }

    @Override
    protected Map<String, Object> buildRequest(ProviderEntity provider, AccountEntity account, Map<String, String> credentials, Map<String, Object> request, String actualModel) {
        Map<String, Object> outgoing = super.buildRequest(provider, account, credentials, request, actualModel);
        outgoing.putIfAbsent("stream", request.getOrDefault("stream", false));
        return outgoing;
    }
}
