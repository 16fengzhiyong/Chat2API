package com.chat2api.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiResponseService {
    private final ObjectMapper objectMapper;

    public OpenAiResponseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toStream(String body, String model) {
        if (body == null || body.isBlank()) {
            return doneOnly();
        }
        if (body.trim().startsWith("data:")) {
            return body.endsWith("\n\n") ? body : body + "\n\n";
        }
        try {
            Map<String, Object> response = objectMapper.readValue(body, new TypeReference<>() {});
            Map<String, Object> message = firstMessage(response);
            String content = message.get("content") == null ? "" : String.valueOf(message.get("content"));
            Object toolCalls = message.get("tool_calls");
            String id = String.valueOf(response.getOrDefault("id", "chatcmpl-" + Instant.now().toEpochMilli()));
            long created = longValue(response.get("created"), Instant.now().getEpochSecond());
            String actualModel = String.valueOf(response.getOrDefault("model", model));
            StringBuilder builder = new StringBuilder();
            builder.append("data: ").append(toJson(chunk(id, created, actualModel, Map.of("role", "assistant"), null))).append("\n\n");
            if (!content.isBlank()) {
                builder.append("data: ").append(toJson(chunk(id, created, actualModel, Map.of("content", content), null))).append("\n\n");
            }
            if (toolCalls instanceof List<?> list && !list.isEmpty()) {
                builder.append("data: ").append(toJson(chunk(id, created, actualModel, Map.of("tool_calls", list), null))).append("\n\n");
            }
            builder.append("data: ").append(toJson(chunk(id, created, actualModel, Map.of(), toolCalls instanceof List<?> list && !list.isEmpty() ? "tool_calls" : "stop"))).append("\n\n");
            builder.append("data: [DONE]\n\n");
            return builder.toString();
        } catch (Exception error) {
            return "data: " + toJson(Map.of("error", Map.of("message", error.getMessage() == null ? "Stream conversion failed" : error.getMessage(), "type", "chat2api_stream_error"))) + "\n\ndata: [DONE]\n\n";
        }
    }

    private Map<String, Object> chunk(String id, long created, String model, Map<String, Object> delta, String finishReason) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", created);
        chunk.put("model", model);
        chunk.put("choices", List.of(choice));
        return chunk;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMessage(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice) {
            Object message = choice.get("message");
            if (message instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        }
        return Map.of();
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception error) {
            return fallback;
        }
    }

    private String doneOnly() {
        return "data: [DONE]\n\n";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{}";
        }
    }
}
