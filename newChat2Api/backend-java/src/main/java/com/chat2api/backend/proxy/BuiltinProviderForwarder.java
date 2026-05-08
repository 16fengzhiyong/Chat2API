package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
            case "zai" -> {
                String token = first(credentials, "token", "accessToken", "access_token");
                if (token != null) {
                    headers.setBearerAuth(token.replaceFirst("(?i)^Bearer\\s+", ""));
                }
                headers.set(HttpHeaders.ORIGIN, "https://chat.z.ai");
                headers.set(HttpHeaders.REFERER, "https://chat.z.ai/");
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
            case "qwen-ai" -> qwenAiRequest(request, actualModel);
            case "zai" -> zaiRequest(request, actualModel);
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
            case "qwen-ai" -> "https://chat.qwen.ai/api/v2/chat/completions";
            case "zai" -> "https://chat.z.ai/api/v2/chat/completions";
            case "perplexity" -> "https://www.perplexity.ai/rest/sse/perplexity_ask";
            case "minimax" -> "https://agent.minimaxi.com/matrix/api/v1/chat/chat_message";
            default -> super.endpoint(provider);
        };
    }

    private Map<String, Object> qwenAiRequest(Map<String, Object> request, String actualModel) {
        long timestamp = Instant.now().toEpochMilli();
        String chatId = String.valueOf(request.getOrDefault("chatId", UUID.randomUUID().toString()));
        String fid = UUID.randomUUID().toString();
        String childId = UUID.randomUUID().toString();
        Map<String, Object> featureConfig = new LinkedHashMap<>();
        featureConfig.put("thinking_enabled", modelLooksLikeThinking(String.valueOf(request.getOrDefault("model", actualModel))));
        featureConfig.put("output_schema", "phase");
        featureConfig.put("auto_search", true);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("fid", fid);
        message.put("parentId", null);
        message.put("childrenIds", List.of(childId));
        message.put("role", "user");
        message.put("content", lastUserContent(request));
        message.put("user_action", "chat");
        message.put("files", List.of());
        message.put("timestamp", timestamp);
        message.put("models", List.of(actualModel));
        message.put("chat_type", "t2t");
        message.put("feature_config", featureConfig);
        message.put("extra", meta());
        message.put("sub_chat_type", "t2t");
        message.put("parent_id", null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stream", true);
        payload.put("version", "2.1");
        payload.put("incremental_output", true);
        payload.put("chat_id", chatId);
        payload.put("chat_mode", "normal");
        payload.put("model", actualModel);
        payload.put("parent_id", null);
        payload.put("messages", List.of(message));
        payload.put("timestamp", timestamp);
        return payload;
    }

    private Map<String, Object> zaiRequest(Map<String, Object> request, String actualModel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stream", request.getOrDefault("stream", true));
        payload.put("model", mapZaiModel(actualModel));
        payload.put("messages", mergeSystemIntoFirstUser(messages(request)));
        payload.put("signature_prompt", lastUserContent(request));
        payload.put("params", Map.of());
        payload.put("extra", Map.of());
        payload.put("features", zaiFeatures(String.valueOf(request.getOrDefault("model", actualModel))));
        payload.put("variables", zaiVariables());
        payload.put("chat_id", String.valueOf(request.getOrDefault("chatId", "")));
        payload.put("id", UUID.randomUUID().toString());
        payload.put("current_user_message_id", UUID.randomUUID().toString());
        payload.put("current_user_message_parent_id", null);
        payload.put("background_tasks", Map.of("title_generation", true, "tags_generation", true));
        return payload;
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

    private Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("subChatType", "t2t");
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("meta", meta);
        return wrapper;
    }

    private Map<String, Object> zaiFeatures(String model) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("image_generation", false);
        features.put("web_search", false);
        features.put("auto_web_search", modelLooksLikeSearch(model));
        features.put("preview_mode", true);
        features.put("flags", List.of());
        features.put("vlm_tools_enable", false);
        features.put("vlm_web_search_enable", false);
        features.put("vlm_website_mode", false);
        features.put("enable_thinking", modelLooksLikeThinking(model));
        return features;
    }

    private Map<String, Object> zaiVariables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("{{USER_NAME}}", "User");
        variables.put("{{USER_LOCATION}}", "Unknown");
        variables.put("{{CURRENT_DATETIME}}", Instant.now().toString());
        variables.put("{{USER_LANGUAGE}}", "en-US");
        return variables;
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

    private List<Map<String, Object>> mergeSystemIntoFirstUser(List<Map<String, Object>> input) {
        StringBuilder system = new StringBuilder();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> message : input) {
            if ("system".equals(message.get("role"))) {
                if (!system.isEmpty()) {
                    system.append("\n\n");
                }
                system.append(message.getOrDefault("content", ""));
            } else {
                result.add(new LinkedHashMap<>(message));
            }
        }
        if (!system.isEmpty()) {
            for (Map<String, Object> message : result) {
                if ("user".equals(message.get("role"))) {
                    message.put("content", system + "\n\nUser: " + message.getOrDefault("content", ""));
                    break;
                }
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

    private String mapZaiModel(String model) {
        return switch (model.toLowerCase()) {
            case "glm-5-turbo" -> "GLM-5-Turbo";
            case "glm-4.6" -> "glm-4.6v";
            default -> model.toLowerCase();
        };
    }

    private boolean modelLooksLikeThinking(String model) {
        String lower = model.toLowerCase();
        return lower.contains("think") || lower.contains("r1") || lower.endsWith("-thinking");
    }

    private boolean modelLooksLikeSearch(String model) {
        return model.toLowerCase().contains("search");
    }
}
