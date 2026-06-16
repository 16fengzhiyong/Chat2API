package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.ProviderEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class QwenAiProtocol {
    static final String BASE_URL = "https://chat.qwen.ai";
    private static final Map<String, String> MODEL_ALIASES = Map.of(
            "qwen", "qwen3-max",
            "qwen3", "qwen3-max",
            "qwen3.5", "qwen3.5-plus",
            "qwen3-coder", "qwen3-coder-plus",
            "qwen3-vl", "qwen3-vl-235b-a22b",
            "qwen3-omni", "qwen3-omni-flash",
            "qwen2.5", "qwen2.5-max",
            "qwen-image", "qwen3.7-plus",
            "qwen-video", "qwen3.7-plus"
    );

    String authCookie(Map<String, String> credentials) {
        String cookie = first(credentials, "cookies", "cookie");
        if (cookie != null && !cookie.isBlank()) {
            return cookie;
        }
        String token = first(credentials, "token", "accessToken", "apiKey");
        return token == null || token.isBlank() ? "" : "token=" + token.replaceFirst("(?i)^Bearer\\s+", "");
    }

    String mapModel(ProviderEntity provider, String model) {
        String mapped = stripModeSuffix(model);
        String lower = mapped.toLowerCase();
        String alias = MODEL_ALIASES.get(lower);
        if (alias != null) {
            return alias;
        }
        if (provider.getModelMappings() != null) {
            for (Map.Entry<String, Object> entry : provider.getModelMappings().entrySet()) {
                if (entry.getKey().toLowerCase().equals(lower) && entry.getValue() != null) {
                    return String.valueOf(entry.getValue());
                }
            }
        }
        return mapped;
    }

    String thinkingMode(Map<String, Object> request, String originalModel, String actualModel, QwenAiOptions options) {
        String model = originalModel == null || originalModel.isBlank() ? actualModel : originalModel;
        String lower = model.toLowerCase();
        if (hasModeSuffix(lower, "thinking")) {
            return QwenAiOptions.THINKING_THINKING;
        }
        if (hasModeSuffix(lower, "fast")) {
            return QwenAiOptions.THINKING_FAST;
        }
        if (lower.contains("think") || lower.contains("r1")) {
            return QwenAiOptions.THINKING_THINKING;
        }
        if (request.get("enable_thinking") != null) {
            return Boolean.TRUE.equals(request.get("enable_thinking")) ? QwenAiOptions.THINKING_THINKING : QwenAiOptions.THINKING_FAST;
        }
        if (request.get("reasoning_effort") != null && !Boolean.FALSE.equals(request.get("reasoning_effort"))) {
            return QwenAiOptions.THINKING_THINKING;
        }
        return options.thinkingMode();
    }

    String chatType(Map<String, Object> request, String originalModel, String actualModel, QwenAiOptions options) {
        String model = originalModel == null || originalModel.isBlank() ? actualModel : originalModel;
        String lower = model.toLowerCase();
        if (lower.equals("qwen-image") || lower.contains("t2i")) {
            return "t2i";
        }
        if (lower.equals("qwen-video") || lower.contains("t2v")) {
            return "t2v";
        }
        if (hasModeSuffix(lower, "search")) {
            return QwenAiOptions.SEARCH_ON;
        }
        Object enableSearch = firstPresent(request, "enable_search", "search");
        if (enableSearch != null) {
            return truthy(enableSearch) ? QwenAiOptions.SEARCH_ON : "t2t";
        }
        return QwenAiOptions.SEARCH_ON.equals(options.searchMode()) ? QwenAiOptions.SEARCH_ON : "t2t";
    }

    boolean isVideoChatType(String chatType) {
        return "t2v".equals(chatType) || "i2v".equals(chatType);
    }

    boolean isImageChatType(String chatType) {
        return "t2i".equals(chatType);
    }

    Map<String, Object> newChatBody(String modelId, String chatMode, String chatType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "OpenAI_API_Chat");
        payload.put("models", List.of(modelId));
        payload.put("chat_mode", chatMode);
        payload.put("chat_type", chatType);
        payload.put("timestamp", Instant.now().toEpochMilli());
        payload.put("project_id", "");
        return payload;
    }

    Map<String, Object> completionBody(Map<String, Object> request, String modelId, String chatId, String parentId, String chatMode, String thinkingMode, String chatType) {
        long timestamp = Instant.now().getEpochSecond();
        boolean isVideo = isVideoChatType(chatType);
        Object size = request.get("size");
        List<Map<String, Object>> imageFiles = extractImageFiles(request);
        boolean hasImages = !imageFiles.isEmpty();

        // i2v: has images in request with video model
        String effectiveChatType = ("t2v".equals(chatType) && hasImages) ? "i2v" : chatType;
        boolean isVideoFinal = "t2v".equals(effectiveChatType) || "i2v".equals(effectiveChatType);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("fid", uuid());
        message.put("parentId", blankToNull(parentId));
        message.put("childrenIds", List.of(uuid()));
        message.put("role", "user");
        message.put("content", promptContent(request));
        message.put("user_action", "chat");
        message.put("files", imageFiles);
        message.put("timestamp", timestamp);
        message.put("models", List.of(modelId));
        message.put("chat_type", effectiveChatType);
        message.put("feature_config", featureConfig(request, thinkingMode, effectiveChatType));
        message.put("extra", meta(effectiveChatType, size));
        message.put("sub_chat_type", effectiveChatType);
        message.put("parent_id", blankToNull(parentId));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stream", !isVideoFinal);
        payload.put("version", "2.1");
        payload.put("incremental_output", true);
        payload.put("chat_id", chatId);
        payload.put("chat_mode", chatMode);
        payload.put("model", modelId);
        payload.put("parent_id", blankToNull(parentId));
        payload.put("messages", List.of(message));
        payload.put("timestamp", timestamp);
        if (size != null && !String.valueOf(size).isBlank()) {
            payload.put("size", String.valueOf(size));
        }
        return payload;
    }

    HttpHeaders headers(String authCookie, String context, String chatId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        headers.set(HttpHeaders.ORIGIN, BASE_URL);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0");
        headers.set("source", "web");
        headers.set("sec-ch-ua", "\"Microsoft Edge\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"");
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"Windows\"");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("bx-v", "2.5.36");
        headers.set("Timezone", "Mon Feb 23 2026 22:06:02 GMT+0800");
        headers.set("Version", "0.2.45");
        headers.set("X-Request-Id", uuid());
        headers.set(HttpHeaders.COOKIE, authCookie);
        if ("new-chat".equals(context)) {
            headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
            headers.set(HttpHeaders.REFERER, BASE_URL + "/c/new-chat");
        } else if ("completion".equals(context)) {
            headers.set(HttpHeaders.REFERER, chatId == null || chatId.isBlank() ? BASE_URL + "/c/new-chat" : BASE_URL + "/c/" + chatId);
            headers.set("x-accel-buffering", "no");
        } else {
            headers.set(HttpHeaders.REFERER, BASE_URL + "/");
        }
        return headers;
    }

    private Map<String, Object> featureConfig(Map<String, Object> request, String thinkingMode, String chatType) {
        boolean thinking = QwenAiOptions.THINKING_THINKING.equals(thinkingMode) || QwenAiOptions.THINKING_AUTO.equals(thinkingMode);
        Map<String, Object> featureConfig = new LinkedHashMap<>();
        featureConfig.put("thinking_enabled", thinking);
        featureConfig.put("output_schema", "phase");
        featureConfig.put("research_mode", "normal");
        featureConfig.put("auto_thinking", QwenAiOptions.THINKING_AUTO.equals(thinkingMode));
        featureConfig.put("thinking_mode", upstreamThinkingMode(thinkingMode));
        featureConfig.put("auto_search", QwenAiOptions.SEARCH_ON.equals(chatType));
        if (QwenAiOptions.THINKING_THINKING.equals(thinkingMode) || QwenAiOptions.THINKING_AUTO.equals(thinkingMode)) {
            featureConfig.put("thinking_format", "summary");
        }
        Object budget = request.get("thinking_budget");
        if (budget != null) {
            featureConfig.put("thinking_budget", budget);
        }
        return featureConfig;
    }

    private String upstreamThinkingMode(String thinkingMode) {
        return switch (thinkingMode) {
            case QwenAiOptions.THINKING_THINKING -> "Thinking";
            case QwenAiOptions.THINKING_FAST -> "Fast";
            default -> "Auto";
        };
    }

    private String promptContent(Map<String, Object> request) {
        StringBuilder system = new StringBuilder();
        String user = "";
        for (Map<String, Object> message : messages(request)) {
            if ("system".equals(message.get("role"))) {
                if (system.length() > 0) {
                    system.append("\n\n");
                }
                system.append(message.getOrDefault("content", ""));
            } else if ("user".equals(message.get("role"))) {
                user = String.valueOf(message.getOrDefault("content", ""));
            }
        }
        return system.length() == 0 ? user : system + "\n\nUser: " + user;
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

    private Map<String, Object> meta(String chatType) {
        return meta(chatType, null);
    }

    private Map<String, Object> meta(String chatType, Object size) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("subChatType", chatType);
        if (size != null && !String.valueOf(size).isBlank()) {
            meta.put("size", String.valueOf(size));
        }
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("meta", meta);
        return wrapper;
    }

    private String stripModeSuffix(String model) {
        String result = model;
        boolean changed;
        do {
            changed = false;
            for (String suffix : List.of("-thinking", "-fast", "-search")) {
                if (result.toLowerCase().endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private boolean hasModeSuffix(String model, String mode) {
        for (String part : model.split("-")) {
            if (mode.equals(part)) {
                return true;
            }
        }
        return false;
    }

    private Object firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text) || "search".equalsIgnoreCase(text);
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

    String parseVideoTaskId(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> parsed = mapper.readValue(responseBody, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        Object data = parsed.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object messages = dataMap.get("messages");
            if (messages instanceof List<?> msgList && !msgList.isEmpty()) {
                Object firstMsg = msgList.get(0);
                if (firstMsg instanceof Map<?, ?> msg) {
                    Object extra = msg.get("extra");
                    if (extra instanceof Map<?, ?> extraMap) {
                        Object wanx = extraMap.get("wanx");
                        if (wanx instanceof Map<?, ?> wanxMap) {
                            Object taskId = wanxMap.get("task_id");
                            return taskId == null ? "" : String.valueOf(taskId);
                        }
                    }
                }
            }
        }
        return "";
    }

    String taskStatusUrl(String taskId) {
        return BASE_URL + "/api/v1/tasks/status/" + taskId;
    }

    private List<Map<String, Object>> extractImageFiles(Map<String, Object> request) {
        List<Map<String, Object>> files = new ArrayList<>();
        List<Map<String, Object>> msgs = messages(request);
        for (Map<String, Object> msg : msgs) {
            if (!"user".equals(msg.get("role"))) continue;
            Object content = msg.get("content");
            if (content instanceof List<?> contentList) {
                for (int i = 0; i < contentList.size(); i++) {
                    Object item = contentList.get(i);
                    if (item instanceof Map<?, ?> part) {
                        Map<?, ?> partMap = (Map<?, ?>) part;
                        if ("image_url".equals(String.valueOf(partMap.get("type")))) {
                            Object imageUrlObj = partMap.get("image_url");
                            String imageUrl = "";
                            if (imageUrlObj instanceof Map<?, ?> imageUrlMap) {
                                Object urlVal = imageUrlMap.get("url");
                                imageUrl = urlVal == null ? "" : String.valueOf(urlVal);
                            } else if (imageUrlObj != null) {
                                imageUrl = String.valueOf(imageUrlObj);
                            }
                            if (!imageUrl.isBlank()) {
                                String fileId = uuid();
                                files.add(Map.of(
                                    "type", "image",
                                    "id", fileId,
                                    "url", imageUrl,
                                    "file_type", "image/png",
                                    "file_class", "vision",
                                    "name", "image_" + i + "_" + System.currentTimeMillis() + ".png",
                                    "status", "uploaded",
                                    "progress", 0
                                ));
                            }
                        }
                    }
                }
            }
        }
        return files;
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private Object blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
