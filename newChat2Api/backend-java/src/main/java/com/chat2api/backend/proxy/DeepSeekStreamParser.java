package com.chat2api.backend.proxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeepSeekStreamParser {
    private final ObjectMapper objectMapper;

    public DeepSeekStreamParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toOpenAiStream(String upstream, String sessionId, String model, Map<String, Object> request) throws Exception {
        ParsedStream parsed = parse(upstream, request);
        long created = Instant.now().getEpochSecond();
        String id = sessionId + "@" + parsed.messageId();
        StringBuilder builder = new StringBuilder();
        builder.append("data: ").append(toJson(chunk(id, model, created, Map.of("role", "assistant"), null))).append("\n\n");
        if (!parsed.reasoning().isBlank()) {
            builder.append("data: ").append(toJson(chunk(id, model, created, Map.of("reasoning_content", parsed.reasoning()), null))).append("\n\n");
        }
        if (!parsed.content().isBlank()) {
            builder.append("data: ").append(toJson(chunk(id, model, created, Map.of("content", parsed.content()), null))).append("\n\n");
        }
        String citations = citations(parsed.searchResults());
        if (!citations.isBlank()) {
            builder.append("data: ").append(toJson(chunk(id, model, created, Map.of("content", "\n\n" + citations), null))).append("\n\n");
        }
        builder.append("data: ").append(toJson(chunk(id, model, created, Map.of(), "stop"))).append("\n\n");
        builder.append("data: [DONE]\n\n");
        return builder.toString();
    }

    public String toOpenAiJson(String upstream, String sessionId, String model, Map<String, Object> request) throws Exception {
        ParsedStream parsed = parse(upstream, request);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", withCitations(parsed.content(), parsed.searchResults()).trim());
        if (!parsed.reasoning().isBlank()) {
            message.put("reasoning_content", parsed.reasoning().trim());
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", sessionId + "@" + parsed.messageId());
        response.put("object", "chat.completion");
        response.put("created", Instant.now().getEpochSecond());
        response.put("model", model);
        response.put("choices", List.of(choice));
        response.put("usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", parsed.tokenUsage()));
        return objectMapper.writeValueAsString(response);
    }

    private ParsedStream parse(String upstream, Map<String, Object> request) throws Exception {
        String model = String.valueOf(request.getOrDefault("model", "")).toLowerCase();
        boolean thinkingModel = model.contains("think") || model.contains("r1") || model.contains("reasoner") || request.get("reasoning_effort") != null;
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<Map<String, Object>> searchResults = new ArrayList<>();
        String currentPath = "";
        String messageId = "";
        int tokenUsage = 2;
        for (String line : upstream.split("\\R")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                continue;
            }
            Map<String, Object> event;
            try {
                event = objectMapper.readValue(data, new TypeReference<>() {});
            } catch (Exception ignored) {
                continue;
            }
            if (messageId.isBlank() && event.get("response_message_id") != null) {
                messageId = String.valueOf(event.get("response_message_id"));
            }
            Object path = event.get("p");
            Object value = event.get("v");
            if (path != null) {
                String pathValue = String.valueOf(path);
                if (pathValue.contains("fragments/-1/content") && !"".equals(currentPath)) {
                    currentPath = currentPath;
                }
            }
            if (value instanceof Map<?, ?> valueMap && valueMap.get("response") instanceof Map<?, ?> response) {
                Object thinkingEnabled = response.get("thinking_enabled");
                if (thinkingEnabled != null) {
                    currentPath = Boolean.TRUE.equals(thinkingEnabled) ? "thinking" : "content";
                }
                String fragmentPath = appendFragments(response.get("fragments"), content, reasoning);
                if (!fragmentPath.isBlank()) {
                    currentPath = fragmentPath;
                }
            } else if ("response/fragments".equals(path)) {
                currentPath = appendFragments(value, content, reasoning);
            } else if (path != null && String.valueOf(path).contains("response/fragments") && String.valueOf(path).contains("/content")) {
                currentPath = fragmentPathFromIndexedPath(String.valueOf(path), currentPath);
            } else if ("response/search_results".equals(path)) {
                updateSearchResults(value, event.get("o"), searchResults);
            } else if ("response".equals(path) && value instanceof List<?> operations) {
                for (Object operation : operations) {
                    if (operation instanceof Map<?, ?> map) {
                        if ("accumulated_token_usage".equals(map.get("p")) && map.get("v") instanceof Number number) {
                            tokenUsage = number.intValue();
                        }
                        if (map.get("v") instanceof Map<?, ?> nested && Boolean.TRUE.equals(nested.get("thinking_enabled"))) {
                            currentPath = "thinking";
                        }
                    }
                }
            }
            if (currentPath.isBlank() && thinkingModel) {
                currentPath = "thinking";
            }
            appendValue(value, currentPath, content, reasoning);
        }
        return new ParsedStream(content.toString(), reasoning.toString(), messageId, tokenUsage, searchResults);
    }

    private String appendFragments(Object fragmentsValue, StringBuilder content, StringBuilder reasoning) {
        String path = "";
        if (fragmentsValue instanceof List<?> fragments) {
            for (Object item : fragments) {
                if (item instanceof Map<?, ?> fragment && fragment.get("content") != null) {
                    String type = String.valueOf(fragment.get("type"));
                    String text = clean(String.valueOf(fragment.get("content")));
                    if ("THINK".equals(type)) {
                        reasoning.append(text);
                        path = "thinking";
                    } else if ("ANSWER".equals(type) || "RESPONSE".equals(type)) {
                        content.append(text);
                        path = "content";
                    }
                }
            }
        }
        return path;
    }

    private String fragmentPathFromIndexedPath(String path, String currentPath) {
        if (path == null || !path.contains("response/fragments")) {
            return currentPath;
        }
        if (path.contains("/-1/")) {
            return currentPath == null || currentPath.isBlank() ? "content" : currentPath;
        }
        return currentPath;
    }

    private void appendValue(Object value, String currentPath, StringBuilder content, StringBuilder reasoning) {
        String text = "";
        if (value instanceof String string) {
            text = clean(string);
        } else if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("v") instanceof List<?> nested) {
                    for (Object nestedItem : nested) {
                        if (nestedItem instanceof Map<?, ?> nestedMap && nestedMap.get("content") != null) {
                            builder.append(nestedMap.get("content"));
                        }
                    }
                }
            }
            text = clean(builder.toString());
        }
        if (text.isBlank()) {
            return;
        }
        if ("thinking".equals(currentPath)) {
            reasoning.append(text);
        } else {
            content.append(text);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateSearchResults(Object value, Object operation, List<Map<String, Object>> searchResults) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        if (!"BATCH".equals(operation)) {
            searchResults.clear();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    map.forEach((key, val) -> result.put(String.valueOf(key), val));
                    searchResults.add(result);
                }
            }
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("p") != null) {
                String p = String.valueOf(map.get("p"));
                if (p.endsWith("/cite_index")) {
                    String indexValue = p.substring(0, p.indexOf('/'));
                    int index = Integer.parseInt(indexValue);
                    if (index >= 0 && index < searchResults.size()) {
                        searchResults.get(index).put("cite_index", map.get("v"));
                    }
                }
            }
        }
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

    private String withCitations(String content, List<Map<String, Object>> searchResults) {
        String citations = citations(searchResults);
        return citations.isBlank() ? content : content + "\n\n" + citations;
    }

    private String citations(List<Map<String, Object>> searchResults) {
        return searchResults.stream()
                .filter(item -> item.get("cite_index") != null)
                .sorted(Comparator.comparingInt(item -> Integer.parseInt(String.valueOf(item.get("cite_index")))))
                .map(item -> "[" + item.get("cite_index") + "]: [" + item.getOrDefault("title", "") + "](" + item.getOrDefault("url", "") + ")")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String clean(String text) {
        return text.replace("FINISHED", "").replaceFirst("(?i)^(SEARCH|WEB_SEARCH|SEARCHING)\\s*", "").replaceAll("\\[citation:(\\d+)]", "[$1]");
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record ParsedStream(String content, String reasoning, String messageId, int tokenUsage, List<Map<String, Object>> searchResults) {}

    public record LineDelta(String contentDelta, String reasoningDelta) {
        public static final LineDelta EMPTY = new LineDelta("", "");
        public boolean isEmpty() { return contentDelta.isBlank() && reasoningDelta.isBlank(); }
    }

    public static final class StreamingContext {
        final StringBuilder content = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        String currentPath = "";
        String messageId = "";
        final boolean thinkingModel;

        public StreamingContext(boolean thinkingModel) {
            this.thinkingModel = thinkingModel;
        }

        public String messageId() { return messageId; }
        public String accumulatedContent() { return content.toString(); }
        public String accumulatedReasoning() { return reasoning.toString(); }
    }

    public LineDelta processLine(String line, StreamingContext ctx) {
        if (!line.startsWith("data:")) return LineDelta.EMPTY;
        String data = line.substring(5).trim();
        if (data.isBlank() || "[DONE]".equals(data)) return LineDelta.EMPTY;
        try {
            Map<String, Object> event = objectMapper.readValue(data, new TypeReference<>() {});
            if (ctx.messageId.isBlank() && event.get("response_message_id") != null) {
                ctx.messageId = String.valueOf(event.get("response_message_id"));
            }
            int prevContent = ctx.content.length();
            int prevReasoning = ctx.reasoning.length();
            Object path = event.get("p");
            Object value = event.get("v");
            if (value instanceof Map<?, ?> valueMap && valueMap.get("response") instanceof Map<?, ?> response) {
                Object thinkingEnabled = response.get("thinking_enabled");
                if (thinkingEnabled != null) {
                    ctx.currentPath = Boolean.TRUE.equals(thinkingEnabled) ? "thinking" : "content";
                }
                String fragmentPath = appendFragments(response.get("fragments"), ctx.content, ctx.reasoning);
                if (!fragmentPath.isBlank()) {
                    ctx.currentPath = fragmentPath;
                }
            } else if ("response/fragments".equals(path)) {
                ctx.currentPath = appendFragments(value, ctx.content, ctx.reasoning);
            } else if (path != null && String.valueOf(path).contains("response/fragments") && String.valueOf(path).contains("/content")) {
                ctx.currentPath = fragmentPathFromIndexedPath(String.valueOf(path), ctx.currentPath);
            } else if ("response".equals(path) && value instanceof List<?> operations) {
                for (Object operation : operations) {
                    if (operation instanceof Map<?, ?> map &&
                            map.get("v") instanceof Map<?, ?> nested &&
                            Boolean.TRUE.equals(nested.get("thinking_enabled"))) {
                        ctx.currentPath = "thinking";
                    }
                }
            }
            if (ctx.currentPath.isBlank() && ctx.thinkingModel) {
                ctx.currentPath = "thinking";
            }
            appendValue(value, ctx.currentPath, ctx.content, ctx.reasoning);
            String contentDelta = ctx.content.length() > prevContent ? ctx.content.substring(prevContent) : "";
            String reasoningDelta = ctx.reasoning.length() > prevReasoning ? ctx.reasoning.substring(prevReasoning) : "";
            return new LineDelta(contentDelta, reasoningDelta);
        } catch (Exception ignored) {
            return LineDelta.EMPTY;
        }
    }
}
