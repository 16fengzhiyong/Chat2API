package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
@Order(-20)
public class ZaiProviderForwarder implements ProviderForwarder {
    private static final String BASE_URL = "https://chat.z.ai";
    private static final String X_FE_VERSION = "prod-fe-1.0.241";
    private static final String SIGNATURE_SECRET = "key-@@@@)))()((9))-xxxx&&&%%%%%";
    private static final Pattern SEARCH_CITATION = Pattern.compile("【[^】]*turn\\d+search\\d+[^】]*】");
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ZaiProviderForwarder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String vendor) {
        return "zai".equals(vendor);
    }

    @Override
    public ForwardResult forward(ProviderEntity provider, AccountEntity account, Map<String, String> credentials, Map<String, Object> request, String actualModel) {
        try {
            String token = first(credentials, "token", "accessToken", "access_token", "jwt");
            if (token == null || token.isBlank()) {
                return ForwardResult.fail(401, "Z.ai token is not configured");
            }
            String mappedModel = mapModel(actualModel);
            List<Map<String, Object>> processedMessages = mergeSystemIntoFirstUser(messages(request));
            String signaturePrompt = lastUserContent(processedMessages);
            ChatInit chat = createChat(mappedModel, signaturePrompt, token);
            String requestId = uuid();
            long timestamp = Instant.now().toEpochMilli();
            String userId = userIdFromToken(token);
            String signature = signature(signaturePrompt, requestId, timestamp, userId);
            Map<String, Object> body = chatBody(request, mappedModel, processedMessages, signaturePrompt, chat.chatId(), chat.messageId(), requestId);
            String url = BASE_URL + "/api/v2/chat/completions?" + query(timestamp, requestId, userId, token, chat.chatId());
            ResponseEntity<String> response = post(url, body, completionHeaders(token, signature, chat.chatId()));
            if (!response.getStatusCode().is2xxSuccessful()) {
                return ForwardResult.fail(response.getStatusCode().value(), response.getBody() == null ? "Z.ai request failed" : response.getBody());
            }
            boolean stream = Boolean.TRUE.equals(request.get("stream"));
            String upstream = response.getBody() == null ? "" : response.getBody();
            return stream
                    ? ForwardResult.ok(200, "text/event-stream; charset=utf-8", toOpenAiStream(upstream, chat.chatId(), mappedModel))
                    : ForwardResult.ok(200, MediaType.APPLICATION_JSON_VALUE, toOpenAiJson(upstream, chat.chatId(), mappedModel));
        } catch (HttpStatusCodeException error) {
            return ForwardResult.fail(error.getStatusCode().value(), error.getResponseBodyAsString());
        } catch (Exception error) {
            return ForwardResult.fail(502, error.getMessage());
        }
    }

    private ChatInit createChat(String model, String firstMessageContent, String token) throws Exception {
        String messageId = uuid();
        long timestampSeconds = Instant.now().getEpochSecond();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", messageId);
        message.put("parentId", null);
        message.put("childrenIds", List.of());
        message.put("role", "user");
        message.put("content", firstMessageContent);
        message.put("timestamp", timestampSeconds);
        message.put("models", List.of(model));
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("messages", firstMessageContent.isBlank() ? Map.of() : Map.of(messageId, message));
        history.put("currentId", firstMessageContent.isBlank() ? "" : messageId);
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("id", "");
        chat.put("title", "New Chat");
        chat.put("models", List.of(model));
        chat.put("params", Map.of());
        chat.put("history", history);
        chat.put("tags", List.of());
        chat.put("flags", List.of());
        chat.put("features", List.of(Map.of("type", "tool_selector", "server", "tool_selector_h", "status", "hidden")));
        chat.put("mcp_servers", List.of());
        chat.put("enable_thinking", false);
        chat.put("auto_web_search", false);
        chat.put("message_version", 1);
        chat.put("extra", Map.of());
        chat.put("timestamp", Instant.now().toEpochMilli());
        ResponseEntity<String> response = post(BASE_URL + "/api/v1/chats/new", Map.of("chat", chat), createHeaders(token));
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to create Z.ai chat: HTTP " + response.getStatusCode().value());
        }
        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Failed to create Z.ai chat: empty response");
        }
        Map<String, Object> parsed = objectMapper.readValue(responseBody, new TypeReference<>() {});
        Object id = parsed.get("id");
        if (id == null || String.valueOf(id).isBlank()) {
            throw new IllegalStateException("Failed to create Z.ai chat: missing chat id");
        }
        return new ChatInit(String.valueOf(id), messageId);
    }

    private Map<String, Object> chatBody(Map<String, Object> request, String model, List<Map<String, Object>> processedMessages, String signaturePrompt, String chatId, String messageId, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        String originalModel = String.valueOf(request.getOrDefault("model", model));
        body.put("stream", request.getOrDefault("stream", true));
        body.put("model", model);
        body.put("messages", processedMessages);
        body.put("signature_prompt", signaturePrompt);
        body.put("params", Map.of());
        body.put("extra", Map.of());
        body.put("features", features(originalModel, request));
        body.put("variables", variables());
        body.put("chat_id", chatId);
        body.put("id", requestId);
        body.put("current_user_message_id", messageId);
        body.put("current_user_message_parent_id", null);
        body.put("background_tasks", Map.of("title_generation", true, "tags_generation", true));
        return body;
    }

    private ResponseEntity<String> post(String url, Object body, HttpHeaders headers) {
        return restTemplate.exchange(URI.create(url), HttpMethod.POST, new HttpEntity<>(toJson(body), headers), String.class);
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = browserHeaders(token, BASE_URL + "/");
        headers.setBearerAuth(stripBearer(token));
        headers.set(HttpHeaders.COOKIE, "token=" + stripBearer(token));
        headers.set("X-FE-Version", X_FE_VERSION);
        return headers;
    }

    private HttpHeaders completionHeaders(String token, String signature, String chatId) {
        HttpHeaders headers = browserHeaders(token, BASE_URL + "/c/" + chatId);
        headers.setBearerAuth(stripBearer(token));
        headers.set(HttpHeaders.COOKIE, "token=" + stripBearer(token));
        headers.set("X-Signature", signature);
        headers.set("X-FE-Version", X_FE_VERSION);
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"");
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"Windows\"");
        headers.set("Priority", "u=1, i");
        return headers;
    }

    private HttpHeaders browserHeaders(String token, String referer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.ALL));
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        headers.set(HttpHeaders.ORIGIN, BASE_URL);
        headers.set(HttpHeaders.REFERER, referer);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36");
        return headers;
    }

    private String toOpenAiStream(String upstream, String chatId, String model) throws Exception {
        ParsedStream parsed = parseStream(upstream);
        long created = Instant.now().getEpochSecond();
        StringBuilder builder = new StringBuilder();
        if (!parsed.reasoning().isBlank()) {
            builder.append("data: ").append(toJson(chunk(chatId, model, created, Map.of("role", "assistant", "reasoning_content", ""), null))).append("\n\n");
            builder.append("data: ").append(toJson(chunk(chatId, model, created, Map.of("reasoning_content", parsed.reasoning()), null))).append("\n\n");
        }
        builder.append("data: ").append(toJson(chunk(chatId, model, created, Map.of("role", "assistant"), null))).append("\n\n");
        if (!parsed.content().isBlank()) {
            builder.append("data: ").append(toJson(chunk(chatId, model, created, Map.of("content", parsed.content()), null))).append("\n\n");
        }
        builder.append("data: ").append(toJson(chunk(chatId, model, created, Map.of(), "stop"))).append("\n\n");
        builder.append("data: [DONE]\n\n");
        return builder.toString();
    }

    private String toOpenAiJson(String upstream, String chatId, String model) throws Exception {
        ParsedStream parsed = parseStream(upstream);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", parsed.content());
        if (!parsed.reasoning().isBlank()) {
            message.put("reasoning_content", parsed.reasoning());
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", chatId);
        response.put("object", "chat.completion");
        response.put("created", Instant.now().getEpochSecond());
        response.put("model", model);
        response.put("choices", List.of(choice));
        return objectMapper.writeValueAsString(response);
    }

    private ParsedStream parseStream(String upstream) throws Exception {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        for (String line : upstream.split("\\R")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                continue;
            }
            Map<String, Object> event = objectMapper.readValue(data, new TypeReference<>() {});
            if (!"chat:completion".equals(event.get("type"))) {
                continue;
            }
            Object eventData = event.get("data");
            if (!(eventData instanceof Map<?, ?> raw)) {
                continue;
            }
            String phase = raw.get("phase") == null ? "" : String.valueOf(raw.get("phase"));
            String delta = raw.get("delta_content") == null ? "" : clean(String.valueOf(raw.get("delta_content")));
            if (delta.isBlank()) {
                continue;
            }
            if ("thinking".equals(phase)) {
                reasoning.append(delta);
            } else if ("answer".equals(phase)) {
                content.append(delta);
            }
        }
        return new ParsedStream(content.toString(), reasoning.toString());
    }

    private Map<String, Object> chunk(String id, String model, long created, Map<String, Object> delta, String finishReason) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("model", model);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("choices", List.of(choice));
        chunk.put("created", created);
        return chunk;
    }

    private String query(long timestamp, String requestId, String userId, String token, String chatId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timestamp", String.valueOf(timestamp));
        params.put("requestId", requestId);
        params.put("user_id", userId);
        params.put("version", "0.0.1");
        params.put("platform", "web");
        params.put("token", stripBearer(token));
        params.put("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36");
        params.put("language", "zh-CN");
        params.put("languages", "zh-CN,zh");
        params.put("timezone", "Asia/Shanghai");
        params.put("cookie_enabled", "true");
        params.put("screen_width", "1512");
        params.put("screen_height", "982");
        params.put("screen_resolution", "1512x982");
        params.put("viewport_height", "945");
        params.put("viewport_width", "923");
        params.put("viewport_size", "923x945");
        params.put("color_depth", "30");
        params.put("pixel_ratio", "2");
        params.put("current_url", BASE_URL + "/c/" + chatId);
        params.put("pathname", "/c/" + chatId);
        params.put("search", "");
        params.put("hash", "");
        params.put("host", "chat.z.ai");
        params.put("hostname", "chat.z.ai");
        params.put("protocol", "https:");
        params.put("referrer", "");
        params.put("title", "Z.ai - Free AI Chatbot & Agent powered by GLM-5 & GLM-4.7");
        params.put("timezone_offset", "-480");
        params.put("local_time", Instant.now().toString());
        params.put("utc_time", Instant.now().toString());
        params.put("is_mobile", "false");
        params.put("is_touch", "false");
        params.put("max_touch_points", "0");
        params.put("browser_name", "Chrome");
        params.put("os_name", "Windows");
        params.put("signature_timestamp", String.valueOf(timestamp));
        List<String> encoded = new ArrayList<>();
        params.forEach((key, value) -> encoded.add(encode(key) + "=" + encode(value)));
        return String.join("&", encoded);
    }

    private Map<String, Object> features(String model, Map<String, Object> request) {
        String lower = model.toLowerCase();
        Object reasoningEffort = request.get("reasoning_effort");
        boolean thinking = (reasoningEffort != null && !Boolean.FALSE.equals(reasoningEffort)) || lower.contains("think") || lower.contains("r1");
        boolean search = Boolean.TRUE.equals(request.get("web_search")) || lower.contains("search");
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("image_generation", false);
        features.put("web_search", false);
        features.put("auto_web_search", search);
        features.put("preview_mode", true);
        features.put("flags", List.of());
        features.put("vlm_tools_enable", false);
        features.put("vlm_web_search_enable", false);
        features.put("vlm_website_mode", false);
        features.put("enable_thinking", thinking);
        return features;
    }

    private Map<String, Object> variables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("{{USER_NAME}}", "User");
        variables.put("{{USER_LOCATION}}", "Unknown");
        variables.put("{{CURRENT_DATETIME}}", Instant.now().toString());
        variables.put("{{CURRENT_DATE}}", Instant.now().toString().substring(0, 10));
        variables.put("{{CURRENT_TIMEZONE}}", "UTC");
        variables.put("{{USER_LANGUAGE}}", "en-US");
        return variables;
    }

    private String signature(String messageText, String requestId, long timestampMs, String userId) throws Exception {
        String metadata = "requestId," + requestId + ",timestamp," + timestampMs + ",user_id," + userId;
        String messageBase64 = Base64.getEncoder().encodeToString(messageText.getBytes(StandardCharsets.UTF_8));
        String canonical = metadata + "|" + messageBase64 + "|" + timestampMs;
        long windowIndex = timestampMs / (5 * 60 * 1000);
        String derivedKeyHex = hmacHex(SIGNATURE_SECRET, String.valueOf(windowIndex));
        return hmacHex(derivedKeyHex, canonical);
    }

    private String hmacHex(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String userIdFromToken(String token) {
        try {
            String stripped = stripBearer(token);
            String[] parts = stripped.split("\\.");
            if (parts.length < 2) {
                return "guest";
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(decoded, new TypeReference<>() {});
            for (String key : List.of("id", "user_id", "uid", "sub")) {
                Object value = payload.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
        } catch (Exception ignored) {
        }
        return "guest";
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

    private String lastUserContent(List<Map<String, Object>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = messages.get(i);
            if ("user".equals(message.get("role"))) {
                return String.valueOf(message.getOrDefault("content", ""));
            }
        }
        return "";
    }

    private String mapModel(String model) {
        return switch (model.toLowerCase()) {
            case "glm-5-turbo" -> "GLM-5-Turbo";
            case "glm-4.6" -> "glm-4.6v";
            default -> model.toLowerCase();
        };
    }

    private String clean(String text) {
        return SEARCH_CITATION.matcher(text).replaceAll("");
    }

    private String first(Map<String, String> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            String value = source.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stripBearer(String token) {
        return token == null ? "" : token.replaceFirst("(?i)^Bearer\\s+", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{}";
        }
    }

    @Override
    public void forwardStreaming(ProviderEntity provider, AccountEntity account,
                                  Map<String, String> credentials, Map<String, Object> request,
                                  String actualModel, SseStreamWriter writer, Consumer<String> onComplete) throws Exception {
        String token = first(credentials, "token", "accessToken", "access_token", "jwt");
        if (token == null || token.isBlank()) {
            writer.writeErrorAndDone("Z.ai token is not configured");
            return;
        }
        String mappedModel = mapModel(actualModel);
        List<Map<String, Object>> processedMessages = mergeSystemIntoFirstUser(messages(request));
        String signaturePrompt = lastUserContent(processedMessages);
        ChatInit chat = createChat(mappedModel, signaturePrompt, token);
        String requestId = uuid();
        long timestamp = Instant.now().toEpochMilli();
        String userId = userIdFromToken(token);
        String signature = signature(signaturePrompt, requestId, timestamp, userId);
        Map<String, Object> body = chatBody(request, mappedModel, processedMessages, signaturePrompt, chat.chatId(), chat.messageId(), requestId);
        String url = BASE_URL + "/api/v2/chat/completions?" + query(timestamp, requestId, userId, token, chat.chatId());
        HttpHeaders headers = completionHeaders(token, signature, chat.chatId());
        byte[] bodyBytes = toJson(body).getBytes(StandardCharsets.UTF_8);

        long created = Instant.now().getEpochSecond();
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        boolean[] roleEmitted = {false};
        boolean[] reasoningHeaderEmitted = {false};

        try {
            restTemplate.execute(URI.create(url), HttpMethod.POST,
                    req -> {
                        req.getHeaders().putAll(headers);
                        req.getBody().write(bodyBytes);
                    },
                    response -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            try {
                                writer.writeErrorAndDone("Z.ai request failed: HTTP " + response.getStatusCode().value());
                            } catch (IOException ignored) {}
                            return null;
                        }
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) continue;
                                String data = line.substring(5).trim();
                                if (data.isBlank() || "[DONE]".equals(data)) continue;
                                try {
                                    Map<String, Object> event = objectMapper.readValue(data, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                                    if (!"chat:completion".equals(event.get("type"))) continue;
                                    Object eventData = event.get("data");
                                    if (!(eventData instanceof Map<?, ?> raw)) continue;
                                    String phase = raw.get("phase") == null ? "" : String.valueOf(raw.get("phase"));
                                    String delta = raw.get("delta_content") == null ? "" : clean(String.valueOf(raw.get("delta_content")));
                                    if (delta.isBlank()) continue;
                                    if (!roleEmitted[0]) {
                                        writer.writeEvent(sseChunk(chat.chatId(), actualModel, created, Map.of("role", "assistant"), null));
                                        roleEmitted[0] = true;
                                    }
                                    if ("thinking".equals(phase)) {
                                        if (!reasoningHeaderEmitted[0]) {
                                            writer.writeEvent(sseChunk(chat.chatId(), actualModel, created, Map.of("reasoning_content", ""), null));
                                            reasoningHeaderEmitted[0] = true;
                                        }
                                        reasoning.append(delta);
                                        writer.writeEvent(sseChunk(chat.chatId(), actualModel, created, Map.of("reasoning_content", delta), null));
                                    } else if ("answer".equals(phase)) {
                                        content.append(delta);
                                        writer.writeEvent(sseChunk(chat.chatId(), actualModel, created, Map.of("content", delta), null));
                                    }
                                } catch (IOException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new IOException(e.getMessage(), e);
                                }
                            }
                        }
                        return null;
                    });
        } catch (HttpStatusCodeException error) {
            writer.writeErrorAndDone("Z.ai request failed: HTTP " + error.getStatusCode().value());
            return;
        }

        writer.writeEvent(sseChunk(chat.chatId(), actualModel, created, Map.of(), "stop"));
        writer.writeDone();
        onComplete.accept(buildCompletionJson(actualModel, chat.chatId(), created, content.toString(), reasoning.toString()));
    }

    private Map<String, Object> sseChunk(String id, String model, long created, Map<String, Object> delta, String finishReason) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("model", model);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("choices", List.of(choice));
        chunk.put("created", created);
        return chunk;
    }

    private String buildCompletionJson(String model, String id, long created, String content, String reasoning) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        if (!reasoning.isBlank()) {
            message.put("reasoning_content", reasoning);
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("object", "chat.completion");
        response.put("created", created);
        response.put("model", model);
        response.put("choices", List.of(choice));
        return toJson(response);
    }

    private record ChatInit(String chatId, String messageId) {}
    private record ParsedStream(String content, String reasoning) {}
}
