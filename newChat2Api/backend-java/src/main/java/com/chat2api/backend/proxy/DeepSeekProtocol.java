package com.chat2api.backend.proxy;

import com.chat2api.backend.support.DeepSeekCredentialSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeepSeekProtocol {
    public static final String BASE_URL = "https://chat.deepseek.com/api";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0";
    public static final String APP_VERSION = "2.0.0";
    public static final String CLIENT_VERSION = "2.0.0";
    public static final String CLIENT_LOCALE = "zh_CN";
    private final ObjectMapper objectMapper;

    public DeepSeekProtocol(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String refreshToken(Map<String, String> credentials) {
        return DeepSeekCredentialSupport.token(credentials);
    }

    public HttpHeaders headers(String token, String cookie, String referer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.ALL));
        applyCommonHeaders(headers, referer);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(stripBearer(token));
        }
        if (cookie != null && !cookie.isBlank()) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }

    public String cookie(Map<String, String> credentials) {
        String existing = DeepSeekCredentialSupport.cookie(credentials);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        long timestamp = Instant.now().toEpochMilli();
        long seconds = timestamp / 1000;
        return "intercom-HWWAFSESTIME=" + timestamp
                + "; HWWAFSESID=" + randomHex(18)
                + "; _frid=" + UUID.randomUUID()
                + "; _fr_ssid=" + UUID.randomUUID()
                + "; _fr_pvid=" + UUID.randomUUID()
                + "; Hm_lpvt=" + seconds;
    }

    public Map<String, Object> sessionBody() {
        return new LinkedHashMap<>();
    }

    public Map<String, Object> challengeBody(String targetPath) {
        return Map.of("target_path", targetPath);
    }

    public Map<String, Object> completionBody(Map<String, Object> request, String sessionId, String prompt, DeepSeekChatOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_session_id", sessionId);
        body.put("parent_message_id", null);
        body.put("prompt", prompt);
        body.put("model_type", options.modelType());
        body.put("ref_file_ids", List.of());
        body.put("search_enabled", options.searchEnabled());
        body.put("thinking_enabled", options.thinkingEnabled());
        body.put("action", request.containsKey("action") ? request.get("action") : null);
        body.put("preempt", false);
        return body;
    }

    public static void applyCommonHeaders(HttpHeaders headers, String referer) {
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
        headers.set(HttpHeaders.ORIGIN, "https://chat.deepseek.com");
        headers.set(HttpHeaders.REFERER, referer == null || referer.isBlank() ? "https://chat.deepseek.com/" : referer);
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set("X-App-Version", APP_VERSION);
        headers.set("X-Client-Locale", CLIENT_LOCALE);
        headers.set("X-Client-Platform", "web");
        headers.set("X-Client-Version", CLIENT_VERSION);
        headers.set("x-Client-Timezone-Offset", "28800");
    }

    public static String sessionReferer(String sessionId) {
        return sessionId == null || sessionId.isBlank()
                ? "https://chat.deepseek.com/"
                : "https://chat.deepseek.com/a/chat/s/" + sessionId;
    }

    public String messagesToPrompt(Map<String, Object> request) {
        Object value = request.get("messages");
        if (!(value instanceof List<?> messages) || messages.isEmpty()) {
            return "";
        }
        List<MessageBlock> blocks = new ArrayList<>();
        for (Object item : messages) {
            if (item instanceof Map<?, ?> raw) {
                Object role = raw.get("role");
                blocks.add(new MessageBlock(role == null ? "user" : String.valueOf(role), content(raw.get("content"))));
            }
        }
        if (blocks.isEmpty()) {
            return "";
        }
        List<MessageBlock> merged = new ArrayList<>();
        MessageBlock current = blocks.get(0);
        for (int i = 1; i < blocks.size(); i++) {
            MessageBlock next = blocks.get(i);
            if (current.role().equals(next.role())) {
                current = new MessageBlock(current.role(), current.text() + "\n\n" + next.text());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < merged.size(); i++) {
            MessageBlock block = merged.get(i);
            if ("assistant".equals(block.role())) {
                prompt.append("<｜Assistant｜>").append(block.text()).append("<｜end of sentence｜>");
            } else if ("user".equals(block.role()) || "system".equals(block.role())) {
                if (i > 0) {
                    prompt.append("<｜User｜>");
                }
                prompt.append(block.text());
            } else if ("tool".equals(block.role())) {
                prompt.append("<｜User｜>").append(block.text());
            } else {
                prompt.append(block.text());
            }
        }
        return prompt.toString().replaceAll("!\\[.+]\\(.+\\)", "");
    }

    @SuppressWarnings("unchecked")
    private String content(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof List<?> parts) {
            List<String> texts = new ArrayList<>();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> map && "text".equals(map.get("type")) && map.get("text") != null) {
                    texts.add(String.valueOf(map.get("text")));
                }
            }
            return String.join("\n", texts);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    public Object nested(Map<String, Object> source, String... keys) {
        Object current = source;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    public String first(Map<String, String> source, String... keys) {
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

    public String stripBearer(String token) {
        return token == null ? "" : token.replaceFirst("(?i)^Bearer\\s+", "");
    }

    private String randomHex(int length) {
        String chars = "0123456789abcdef";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt((int) Math.floor(Math.random() * chars.length())));
        }
        return builder.toString();
    }

    private record MessageBlock(String role, String text) {}
}
