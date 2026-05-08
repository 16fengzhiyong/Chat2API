package com.chat2api.backend.proxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QwenAiStreamParser {
    private final ObjectMapper objectMapper;

    QwenAiStreamParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String toOpenAiStream(String upstream, String chatId, String model) throws Exception {
        ParsedStream parsed = parse(upstream, chatId);
        long created = Instant.now().getEpochSecond();
        StringBuilder builder = new StringBuilder();
        if (!parsed.reasoning().isBlank()) {
            builder.append("data: ").append(toJson(chunk(parsed.responseId(), model, created, delta("role", "assistant", "reasoning_content", ""), null))).append("\n\n");
            builder.append("data: ").append(toJson(chunk(parsed.responseId(), model, created, delta("reasoning_content", parsed.reasoning()), null))).append("\n\n");
        }
        builder.append("data: ").append(toJson(chunk(parsed.responseId(), model, created, delta("role", "assistant", "content", ""), null))).append("\n\n");
        if (!parsed.content().isBlank()) {
            builder.append("data: ").append(toJson(chunk(parsed.responseId(), model, created, delta("content", parsed.content()), null))).append("\n\n");
        }
        builder.append("data: ").append(toJson(chunk(parsed.responseId(), model, created, Map.of(), finishReason(parsed)))).append("\n\n");
        builder.append("data: [DONE]\n\n");
        return builder.toString();
    }

    String toOpenAiJson(String upstream, String chatId, String model) throws Exception {
        ParsedStream parsed = parse(upstream, chatId);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", parsed.content());
        if (!parsed.reasoning().isBlank()) {
            message.put("reasoning_content", parsed.reasoning());
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", finishReason(parsed));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", parsed.responseId());
        response.put("object", "chat.completion");
        response.put("created", Instant.now().getEpochSecond());
        response.put("model", model);
        response.put("choices", List.of(choice));
        response.put("usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2));
        return objectMapper.writeValueAsString(response);
    }

    private ParsedStream parse(String upstream, String chatId) throws Exception {
        String responseId = chatId;
        String finishReason = "";
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        String summary = "";
        for (String eventData : dataEvents(upstream)) {
            if (eventData.isBlank() || "[DONE]".equals(eventData)) {
                continue;
            }
            Map<String, Object> event = objectMapper.readValue(eventData, new TypeReference<>() {});
            responseId = responseId(event, responseId);
            Object choices = event.get("choices");
            if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty() || !(choiceList.get(0) instanceof Map<?, ?> choice)) {
                continue;
            }
            Object deltaValue = choice.get("delta");
            if (!(deltaValue instanceof Map<?, ?> delta)) {
                continue;
            }
            String phase = string(delta.get("phase"));
            String status = string(delta.get("status"));
            String text = string(delta.get("content"));
            if ("think".equals(phase) && !"finished".equals(status)) {
                reasoning.append(text);
            } else if ("thinking_summary".equals(phase)) {
                String nextSummary = summary(delta.get("extra"));
                if (nextSummary.length() > summary.length()) {
                    summary = nextSummary;
                }
            } else if ("answer".equals(phase) || (phase.isBlank() && !text.isBlank())) {
                content.append(text);
                if ("finished".equals(status)) {
                    finishReason = string(delta.get("finish_reason"));
                }
            }
        }
        String finalReasoning = reasoning.length() == 0 ? summary : reasoning.toString();
        return new ParsedStream(content.toString(), finalReasoning, responseId, finishReason);
    }

    private List<String> dataEvents(String upstream) {
        List<String> events = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : upstream.split("\\R")) {
            if (line.startsWith("data:")) {
                current.append(line.substring(5).trim());
            } else if (line.isBlank() && current.length() > 0) {
                events.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            events.add(current.toString());
        }
        return events;
    }

    private String responseId(Map<String, Object> event, String fallback) {
        Object created = event.get("response.created");
        if (created instanceof Map<?, ?> createdMap) {
            Object id = createdMap.get("response_id");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        }
        return fallback;
    }

    private String summary(Object extra) {
        if (!(extra instanceof Map<?, ?> extraMap)) {
            return "";
        }
        Object summaryThought = extraMap.get("summary_thought");
        if (!(summaryThought instanceof Map<?, ?> summaryMap)) {
            return "";
        }
        Object value = summaryMap.get("content");
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    parts.add(String.valueOf(item));
                }
            }
            return String.join("\n", parts);
        }
        return string(value);
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

    private Map<String, Object> delta(String key, Object value) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put(key, value);
        return delta;
    }

    private Map<String, Object> delta(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put(firstKey, firstValue);
        delta.put(secondKey, secondValue);
        return delta;
    }

    private String finishReason(ParsedStream parsed) {
        return parsed.finishReason().isBlank() ? "stop" : parsed.finishReason();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record ParsedStream(String content, String reasoning, String responseId, String finishReason) {}
}
