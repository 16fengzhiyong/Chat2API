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
                provider("deepseek", "DeepSeek", "deepseek", "userToken", "https://chat.deepseek.com/api", "/v0/chat/completion", List.of("deepseek-v4-pro", "DeepSeek-R1"), Map.of("deepseek-v4-pro", "deepseek-chat", "DeepSeek-R1", "deepseek-chat")),
                provider("glm", "GLM", "glm", "refresh_token", "https://chatglm.cn/api", "/chatglm/backend-api/assistant/stream", List.of("GLM-5"), Map.of("GLM-5", "glm-5")),
                provider("kimi", "Kimi", "kimi", "jwt", "https://www.kimi.com", "/apiv2/kimi.gateway.chat.v1.ChatService/Chat", List.of("Kimi-K2.6", "Kimi-K2.5"), Map.of("Kimi-K2.6", "kimi-k2.6", "Kimi-K2.5", "kimi-k2.5")),
                provider("qwen", "Qwen", "qwen", "tongyi_sso_ticket", "https://chat2.qianwen.com", "/api/v2/chat", List.of("Qwen3", "Qwen3-Max", "Qwen3-Coder"), Map.of("Qwen3", "tongyi-qwen3-max-model-agent", "Qwen3-Max", "tongyi-qwen3-max-model-agent", "Qwen3-Coder", "qwen3-coder-plus")),
                provider("qwen-ai", "Qwen AI (International)", "qwen-ai", "cookie", "https://chat.qwen.ai", "/api/v2/chat/completions", List.of("Qwen3.6-Plus", "Qwen3.5-Plus", "Qwen2.5-Max"), Map.of("Qwen3.6-Plus", "qwen3.6-plus", "Qwen3.5-Plus", "qwen3.5-plus", "Qwen2.5-Max", "qwen-max-latest")),
                provider("zai", "Z.ai", "zai", "jwt", "https://chat.z.ai/api", "/v2/chat/completions", List.of("GLM-5-Turbo", "glm-5", "glm-4.7"), Map.of("GLM-5-Turbo", "GLM-5-Turbo", "glm-5", "glm-5", "glm-4.7", "glm-4.7")),
                provider("minimax", "MiniMax", "minimax", "jwt", "https://agent.minimaxi.com", "/matrix/api/v1/chat/send_msg", List.of("MiniMax-M2.5", "MiniMax-M2.7"), Map.of("MiniMax-M2.5", "MiniMax-M2.5", "MiniMax-M2.7", "MiniMax-M2.7")),
                provider("mimo", "Mimo", "mimo", "cookie", "https://aistudio.xiaomimimo.com", "/open-apis/bot/chat", List.of("MiMo-V2.5-Pro", "MiMo-V2.5", "MiMo-V2-Flash"), Map.of("MiMo-V2.5-Pro", "mimo-v2.5-pro", "MiMo-V2.5", "mimo-v2.5", "MiMo-V2-Flash", "mimo-v2-flash")),
                provider("perplexity", "Perplexity", "perplexity", "cookie", "https://www.perplexity.ai", "/rest/sse/perplexity_ask", List.of("Auto", "Turbo", "PPLX-Pro"), Map.of("Auto", "auto", "Turbo", "turbo", "PPLX-Pro", "pplx_pro"))
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
