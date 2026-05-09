package com.chat2api.backend.proxy;

import java.util.Map;

public record DeepSeekChatOptions(String modelType, boolean searchEnabled, boolean thinkingEnabled) {
    public static DeepSeekChatOptions resolve(Map<String, Object> request, String prompt) {
        String model = String.valueOf(request.getOrDefault("model", "")).toLowerCase();
        Object webSearch = request.get("web_search");
        Object reasoningEffort = request.get("reasoning_effort");
        boolean search = Boolean.TRUE.equals(webSearch) || model.contains("search");
        boolean thinking = (reasoningEffort != null && !Boolean.FALSE.equals(reasoningEffort))
                || model.contains("r1")
                || model.contains("think")
                || model.contains("reasoner")
                || (prompt != null && prompt.contains("deep thinking"));
        String modelType = model.contains("pro") || model.contains("expert") ? "expert" : "default";
        return new DeepSeekChatOptions(modelType, search, thinking);
    }
}
