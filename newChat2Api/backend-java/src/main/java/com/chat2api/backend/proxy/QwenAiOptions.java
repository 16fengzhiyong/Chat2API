package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.ProviderEntity;

import java.util.Locale;
import java.util.Map;

final class QwenAiOptions {
    static final String THINKING_AUTO = "auto";
    static final String THINKING_THINKING = "thinking";
    static final String THINKING_FAST = "fast";
    static final String RECORD_NORMAL = "record";
    static final String RECORD_LOCAL = "local";
    static final String SEARCH_AUTO = "auto";
    static final String SEARCH_OFF = "off";
    static final String SEARCH_ON = "search";

    private final String thinkingMode;
    private final String recordMode;
    private final String searchMode;

    private QwenAiOptions(String thinkingMode, String recordMode, String searchMode) {
        this.thinkingMode = thinkingMode;
        this.recordMode = recordMode;
        this.searchMode = searchMode;
    }

    static QwenAiOptions from(ProviderEntity provider) {
        Map<String, Object> qwen = qwenSettings(provider == null ? null : provider.getSettings());
        return new QwenAiOptions(
                normalizeThinking(string(qwen.get("thinkingMode"))),
                normalizeRecord(string(qwen.get("recordMode"))),
                normalizeSearch(string(qwen.get("searchMode")))
        );
    }

    String thinkingMode() {
        return thinkingMode;
    }

    String chatMode() {
        return RECORD_LOCAL.equals(recordMode) ? "local" : "normal";
    }

    boolean recordMode() {
        return RECORD_NORMAL.equals(recordMode);
    }

    String searchMode() {
        return searchMode;
    }

    static Map<String, Object> qwenSettings(Map<String, Object> settings) {
        if (settings == null) {
            return Map.of();
        }
        Object value = settings.get("qwen");
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String normalizeThinking(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case THINKING_THINKING -> THINKING_THINKING;
            case THINKING_FAST -> THINKING_FAST;
            default -> THINKING_AUTO;
        };
    }

    private static String normalizeRecord(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return RECORD_LOCAL.equals(normalized) ? RECORD_LOCAL : RECORD_NORMAL;
    }

    private static String normalizeSearch(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case SEARCH_ON, "on", "true", "enabled" -> SEARCH_ON;
            case SEARCH_OFF, "normal", "false", "disabled" -> SEARCH_OFF;
            default -> SEARCH_AUTO;
        };
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
