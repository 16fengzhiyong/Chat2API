package com.chat2api.backend.service;

import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.domain.ProviderType;
import com.chat2api.backend.repository.ProviderRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BuiltinProviderSeeder {
    private final ProviderRepository providerRepository;

    public BuiltinProviderSeeder(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @PostConstruct
    public void seed() {
        builtinProviders().forEach(provider -> providerRepository.findById(provider.getId()).orElseGet(() -> providerRepository.save(provider)));
    }

    private List<ProviderEntity> builtinProviders() {
        return List.of(
                provider("zai", "Z.ai", "zai", "jwt", "https://chat.z.ai/api", "/v2/chat/completions", List.of("GLM-5-Turbo", "glm-5", "glm-4.7"), Map.of("GLM-5-Turbo", "GLM-5-Turbo", "glm-5", "glm-5", "glm-4.7", "glm-4.7")),
                provider("deepseek", "DeepSeek", "deepseek", "userToken", "https://chat.deepseek.com/api", "/v0/chat/completion", List.of(
                        "deepseek-v4-pro",
                        "deepseek-v4-pro-think",
                        "deepseek-v4-pro-search",
                        "deepseek-v4-pro-think-search",
                        "deepseek-v4-flash",
                        "deepseek-v4-flash-think",
                        "deepseek-v4-flash-search",
                        "deepseek-v4-flash-think-search",
                        "deepseek-chat",
                        "deepseek-reasoner",
                        "DeepSeek-V3.2",
                        "DeepSeek-Search",
                        "DeepSeek-R1",
                        "DeepSeek-R1-Search"
                ), Map.ofEntries(
                        Map.entry("deepseek-v4-pro", "deepseek-chat"),
                        Map.entry("deepseek-v4-pro-think", "deepseek-chat"),
                        Map.entry("deepseek-v4-pro-search", "deepseek-chat"),
                        Map.entry("deepseek-v4-pro-think-search", "deepseek-chat"),
                        Map.entry("deepseek-v4-flash", "deepseek-chat"),
                        Map.entry("deepseek-v4-flash-think", "deepseek-chat"),
                        Map.entry("deepseek-v4-flash-search", "deepseek-chat"),
                        Map.entry("deepseek-v4-flash-think-search", "deepseek-chat"),
                        Map.entry("deepseek-chat", "deepseek-chat"),
                        Map.entry("deepseek-reasoner", "deepseek-chat"),
                        Map.entry("DeepSeek-V3.2", "deepseek-chat"),
                        Map.entry("DeepSeek-Search", "deepseek-chat"),
                        Map.entry("DeepSeek-R1", "deepseek-chat"),
                        Map.entry("DeepSeek-R1-Search", "deepseek-chat")
                )),
                provider("qwen-ai", "Qwen AI", "qwen-ai", "cookie", "https://chat.qwen.ai", "/api/v2/chat/completions", List.of("Qwen3.6-Plus", "Qwen3.5-Plus", "Qwen2.5-Max"), Map.of("Qwen3.6-Plus", "qwen3.6-plus", "Qwen3.5-Plus", "qwen3.5-plus", "Qwen2.5-Max", "qwen-max-latest"))
        );
    }

    private ProviderEntity provider(String id, String name, String vendor, String authType, String endpoint, String chatPath, List<String> models, Map<String, String> mappings) {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(id);
        provider.setName(name);
        provider.setType(ProviderType.BUILTIN);
        provider.setVendor(vendor);
        provider.setAuthType(authType);
        provider.setApiEndpoint(endpoint);
        provider.setChatPath(chatPath);
        provider.setSupportedModels(models);
        Map<String, Object> modelMappings = new LinkedHashMap<>();
        mappings.forEach(modelMappings::put);
        provider.setModelMappings(modelMappings);
        provider.setHeaders(defaultHeaders(endpoint));
        provider.setDescription(name + " built-in provider");
        return provider;
    }

    private Map<String, Object> defaultHeaders(String endpoint) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "*/*");
        headers.put("Origin", endpoint.replace("/api", ""));
        headers.put("Referer", endpoint.replace("/api", "") + "/");
        return headers;
    }
}
