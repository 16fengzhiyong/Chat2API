package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Order(0)
public class BuiltinProviderForwarder extends BaseProviderForwarder {
    private static final Set<String> SUPPORTED = Set.of();

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
            case "mimo", "perplexity" -> {
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
        return switch (provider.getVendor()) {
            case "perplexity" -> perplexityRequest(request, actualModel);
            case "minimax" -> minimaxRequest(request, actualModel);
            default -> {
                Map<String, Object> outgoing = super.buildRequest(provider, account, credentials, request, actualModel);
                outgoing.putIfAbsent("stream", request.getOrDefault("stream", false));
                yield outgoing;
            }
        };
    }

    @Override
    protected String endpoint(ProviderEntity provider) {
        return switch (provider.getVendor()) {
            case "perplexity" -> "https://www.perplexity.ai/rest/sse/perplexity_ask";
            case "minimax" -> "https://agent.minimaxi.com/matrix/api/v1/chat/chat_message";
            default -> super.endpoint(provider);
        };
    }

    private Map<String, Object> perplexityRequest(Map<String, Object> request, String actualModel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query_str", lastUserContent(request));
        payload.put("params", Map.of("mode", "concise", "model_preference", actualModel, "source", "default"));
        payload.put("source", "default");
        payload.put("version", "2.18");
        payload.put("frontend_uuid", UUID.randomUUID().toString());
        return payload;
    }

    private Map<String, Object> minimaxRequest(Map<String, Object> request, String actualModel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", actualModel);
        payload.put("messages", messages(request));
        payload.put("stream", request.getOrDefault("stream", false));
        payload.put("bot_setting", List.of());
        payload.put("reply_constraints", Map.of("sender_type", "BOT", "sender_name", "Assistant"));
        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(Map<String, Object> request) {
        Object value = request.get("messages");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> message = new LinkedHashMap<>();
                map.forEach((key, val) -> message.put(String.valueOf(key), val));
                result.add(message);
            }
        }
        return result;
    }

    private String lastUserContent(Map<String, Object> request) {
        List<Map<String, Object>> messages = messages(request);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = messages.get(i);
            if ("user".equals(message.get("role"))) {
                return String.valueOf(message.getOrDefault("content", ""));
            }
        }
        return "";
    }

}
