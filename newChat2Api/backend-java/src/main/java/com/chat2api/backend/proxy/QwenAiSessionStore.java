package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.SessionEntity;
import com.chat2api.backend.repository.SessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class QwenAiSessionStore {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final SessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    QwenAiSessionStore(SessionRepository sessionRepository, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    State load(Map<String, Object> request, boolean recordMode, String chatMode, String chatType) {
        if (!recordMode) {
            return State.empty();
        }
        String sessionId = string(request.get("sessionId"));
        if (sessionId.isBlank()) {
            return State.empty();
        }
        return sessionRepository.findById(sessionId)
                .map(session -> metadata(session).get("qwenAi"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(source -> modeState(source, modeKey(chatMode, chatType)))
                .orElseGet(State::empty);
    }

    void save(Map<String, Object> request, boolean recordMode, String chatMode, String chatType, String chatId, String parentId) {
        if (!recordMode || chatId == null || chatId.isBlank() || parentId == null || parentId.isBlank()) {
            return;
        }
        String sessionId = string(request.get("sessionId"));
        if (sessionId.isBlank()) {
            return;
        }
        Optional<SessionEntity> found = sessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return;
        }
        SessionEntity session = found.get();
        Map<String, Object> metadata = metadata(session);
        Map<String, Object> qwen = qwenState(metadata.get("qwenAi"));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("chatId", chatId);
        state.put("parentId", parentId);
        qwen.put(modeKey(chatMode, chatType), state);
        metadata.put("qwenAi", qwen);
        session.setMetadataJson(toJson(metadata));
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
    }

    private State modeState(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> mode) {
            return state(mode);
        }
        if (source.containsKey("chatId")) {
            return state(source);
        }
        return State.empty();
    }

    private Map<String, Object> qwenState(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> source) {
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private String modeKey(String chatMode, String chatType) {
        return string(chatMode) + ":" + string(chatType);
    }

    private State state(Map<?, ?> source) {
        return new State(string(source.get("chatId")), string(source.get("parentId")));
    }

    private Map<String, Object> metadata(SessionEntity session) {
        try {
            return new LinkedHashMap<>(objectMapper.readValue(session.getMetadataJson(), MAP_TYPE));
        } catch (Exception error) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception error) {
            return "{}";
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record State(String chatId, String parentId) {
        static State empty() {
            return new State("", "");
        }

        boolean hasChat() {
            return chatId != null && !chatId.isBlank();
        }
    }
}
