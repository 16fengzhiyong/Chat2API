package com.chat2api.backend.proxy;

import java.util.Map;

public record DeepSeekChatOptions(String modelType, boolean searchEnabled, boolean thinkingEnabled) {
    public static DeepSeekChatOptions resolve(Map<String, Object> request, String prompt) {
        String model = String.valueOf(request.getOrDefault("model", "")).toLowerCase();
        Object webSearch = request.get("web_search");
        Object reasoningEffort = request.get("reasoning_effort");
        boolean search = Boolean.TRUE.equals(webSearch) || model.contains("search");
        boolean thinking = truthy(request.get("thinking_enabled"))
                || truthy(request.get("enable_thinking"))
                || (reasoningEffort != null && !Boolean.FALSE.equals(reasoningEffort))
                || model.contains("r1")
                || model.contains("think")
                || model.contains("reasoner")
                || (prompt != null && prompt.contains("deep thinking"));
        String modelType = model.contains("expert") || model.contains("pro") ? "expert" : "default";
        return new DeepSeekChatOptions(modelType, search, thinking);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }
}
