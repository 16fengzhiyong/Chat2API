package com.chat2api.backend.web;

import com.chat2api.backend.proxy.ForwardResult;
import com.chat2api.backend.proxy.ProxyService;
import com.chat2api.backend.repository.AppConfigRepository;
import com.chat2api.backend.repository.ModelMappingRepository;
import com.chat2api.backend.repository.ProviderRepository;
import com.chat2api.backend.service.ApiKeyService;
import com.chat2api.backend.service.OpenAiResponseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class OpenAiController {
    private final ProxyService proxyService;
    private final ProviderRepository providerRepository;
    private final ModelMappingRepository modelMappingRepository;
    private final ApiKeyService apiKeyService;
    private final OpenAiResponseService openAiResponseService;
    private final AppConfigRepository appConfigRepository;

    public OpenAiController(ProxyService proxyService, ProviderRepository providerRepository, ModelMappingRepository modelMappingRepository, ApiKeyService apiKeyService, OpenAiResponseService openAiResponseService, AppConfigRepository appConfigRepository) {
        this.proxyService = proxyService;
        this.providerRepository = providerRepository;
        this.modelMappingRepository = modelMappingRepository;
        this.apiKeyService = apiKeyService;
        this.openAiResponseService = openAiResponseService;
        this.appConfigRepository = appConfigRepository;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of("name", "Chat2API Backend", "version", "1.0.0", "timestamp", Instant.now().toString());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "timestamp", Instant.now().toString());
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<String> chatCompletions(@RequestBody Map<String, Object> request, HttpServletRequest servletRequest) {
        if (!authorized(servletRequest)) {
            return ResponseEntity.status(401).contentType(MediaType.APPLICATION_JSON).body(openAiError("Invalid API key"));
        }
        ForwardResult result = proxyService.chatCompletion(request);
        if (Boolean.TRUE.equals(request.get("stream"))) {
            return ResponseEntity.status(result.statusCode())
                    .header(HttpHeaders.CONTENT_TYPE, "text/event-stream; charset=utf-8")
                    .body(result.success() ? openAiResponseService.toStream(result.body(), String.valueOf(request.getOrDefault("model", ""))) : "data: " + openAiError(result.errorMessage()) + "\n\ndata: [DONE]\n\n");
        }
        return ResponseEntity.status(result.statusCode())
                .header(HttpHeaders.CONTENT_TYPE, result.contentType() == null ? MediaType.APPLICATION_JSON_VALUE : result.contentType())
                .body(result.success() ? result.body() : openAiError(result.errorMessage()));
    }

    @PostMapping("/v1/completions")
    public ResponseEntity<String> completions(@RequestBody Map<String, Object> request, HttpServletRequest servletRequest) {
        if (!authorized(servletRequest)) {
            return ResponseEntity.status(401).contentType(MediaType.APPLICATION_JSON).body(openAiError("Invalid API key"));
        }
        request.putIfAbsent("messages", List.of(Map.of("role", "user", "content", String.valueOf(request.getOrDefault("prompt", "")))));
        return chatCompletions(request, servletRequest);
    }

    @GetMapping("/v1/models")
    public Map<String, Object> models() {
        List<Map<String, Object>> models = providerRepository.findByEnabledTrue().stream()
                .flatMap(provider -> provider.getSupportedModels().stream())
                .distinct()
                .map(model -> Map.<String, Object>of("id", model, "object", "model", "created", 0, "owned_by", "chat2api"))
                .toList();
        return Map.of("object", "list", "data", models);
    }

    @GetMapping("/v1/models/{model}")
    public ResponseEntity<Map<String, Object>> model(@PathVariable String model) {
        boolean exists = providerRepository.findByEnabledTrue().stream().anyMatch(provider -> provider.getSupportedModels().contains(model)) || modelMappingRepository.existsById(model);
        if (!exists) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("message", "Model not found", "type", "invalid_request_error")));
        }
        return ResponseEntity.ok(Map.of("id", model, "object", "model", "created", 0, "owned_by", "chat2api"));
    }

    private String openAiError(String message) {
        return "{\"error\":{\"message\":" + jsonString(message == null ? "Request failed" : message) + ",\"type\":\"chat2api_error\"}}";
    }

    private boolean authorized(HttpServletRequest request) {
        boolean apiKeyEnabled = appConfigRepository.findById("apiKeyEnabled")
                .map(e -> "true".equalsIgnoreCase(e.getConfigValue()))
                .orElse(false);
        if (!apiKeyEnabled) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String apiKey = request.getHeader("X-API-Key");
        String queryKey = request.getParameter("api_key");
        String token = apiKey != null ? apiKey : queryKey;
        if (token == null && authorization != null && authorization.toLowerCase().startsWith("bearer ")) {
            token = authorization.substring(7);
        }
        return token != null && apiKeyService.verify(token).isPresent();
    }

    private String jsonString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
