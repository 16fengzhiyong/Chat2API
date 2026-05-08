package com.chat2api.backend.service;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.domain.SessionEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import com.chat2api.backend.repository.SessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SessionService {
    private static final String CONFIG_KEY = "sessionManagement";
    private final SessionRepository sessionRepository;
    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;
    private final IdService idService;

    public SessionService(SessionRepository sessionRepository, AppConfigRepository appConfigRepository, ObjectMapper objectMapper, IdService idService) {
        this.sessionRepository = sessionRepository;
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
        this.idService = idService;
    }

    @Transactional
    public SessionContext prepare(Map<String, Object> request, String providerId, String accountId, String model) {
        Map<String, Object> config = config();
        if (!Boolean.TRUE.equals(config.get("enabled"))) {
            return new SessionContext(request, Optional.empty(), false);
        }
        String sessionId = request.get("sessionId") == null ? null : String.valueOf(request.get("sessionId"));
        Optional<SessionEntity> existing = activeSession(sessionId, providerId, accountId);
        SessionEntity session = existing.orElseGet(() -> create(providerId, accountId, model, config));
        List<Map<String, Object>> history = metadata(session).messages();
        List<Map<String, Object>> incoming = messages(request.get("messages"));
        Map<String, Object> updated = new LinkedHashMap<>(request);
        if (Boolean.TRUE.equals(config.get("multiTurn")) && !history.isEmpty()) {
            List<Map<String, Object>> merged = new ArrayList<>(history);
            merged.addAll(incoming);
            updated.put("messages", merged);
        }
        updated.put("sessionId", session.getId());
        return new SessionContext(updated, Optional.of(session), existing.isEmpty());
    }

    @Transactional
    public void complete(SessionContext context, Map<String, Object> request, String responseBody) {
        if (context.session().isEmpty()) {
            return;
        }
        SessionEntity session = context.session().get();
        Map<String, Object> config = config();
        if (Boolean.TRUE.equals(config.get("deleteAfterChat"))) {
            sessionRepository.deleteById(session.getId());
            return;
        }
        List<Map<String, Object>> messages = messages(request.get("messages"));
        Map<String, Object> assistant = assistantMessage(responseBody);
        if (!assistant.isEmpty()) {
            messages.add(assistant);
        }
        SessionMetadata metadata = new SessionMetadata(messages);
        session.setMetadataJson(toJson(Map.of("messages", metadata.messages())));
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
    }

    public Map<String, Object> config() {
        return appConfigRepository.findById(CONFIG_KEY)
                .map(AppConfigEntity::getConfigValue)
                .map(this::parseConfig)
                .orElseGet(this::defaultConfig);
    }

    public String defaultConfigJson() {
        return toJson(defaultConfig());
    }

    private Optional<SessionEntity> activeSession(String sessionId, String providerId, String accountId) {
        Instant now = Instant.now();
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionRepository.findById(sessionId).filter(session -> active(session, now));
        }
        return sessionRepository.findByProviderIdAndAccountId(providerId, accountId).stream().filter(session -> active(session, now)).findFirst();
    }

    private boolean active(SessionEntity session, Instant now) {
        return "active".equals(session.getStatus()) && (session.getExpiresAt() == null || session.getExpiresAt().isAfter(now));
    }

    private SessionEntity create(String providerId, String accountId, String model, Map<String, Object> config) {
        SessionEntity session = new SessionEntity();
        session.setId(idService.id("session"));
        session.setProviderId(providerId);
        session.setAccountId(accountId);
        session.setModel(model);
        session.setStatus("active");
        session.setMetadataJson(toJson(Map.of("messages", List.of())));
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(intValue(config.get("sessionTimeoutMinutes"), 60), ChronoUnit.MINUTES));
        return sessionRepository.save(session);
    }

    private SessionMetadata metadata(SessionEntity session) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(session.getMetadataJson(), new TypeReference<>() {});
            return new SessionMetadata(messages(parsed.get("messages")));
        } catch (Exception error) {
            return new SessionMetadata(List.of());
        }
    }

    private Map<String, Object> assistantMessage(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object choices = response.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> map) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    map.forEach((key, val) -> result.put(String.valueOf(key), val));
                    return result;
                }
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> merged = defaultConfig();
            merged.putAll(parsed);
            return merged;
        } catch (Exception error) {
            return defaultConfig();
        }
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("multiTurn", true);
        config.put("deleteAfterChat", false);
        config.put("sessionTimeoutMinutes", 60);
        return config;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(Object value) {
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{}";
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception error) {
            return fallback;
        }
    }

    public record SessionContext(Map<String, Object> request, Optional<SessionEntity> session, boolean created) {}
    private record SessionMetadata(List<Map<String, Object>> messages) {}
}
