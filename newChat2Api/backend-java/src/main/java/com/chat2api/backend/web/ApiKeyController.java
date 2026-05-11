package com.chat2api.backend.web;

import com.chat2api.backend.domain.ApiKeyEntity;
import com.chat2api.backend.service.ApiKeyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public ApiResponse<List<ApiKeyEntity>> list() {
        return ApiResponse.ok(apiKeyService.list());
    }

    @PostMapping
    public ApiResponse<ApiKeyEntity> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(apiKeyService.create(String.valueOf(request.getOrDefault("name", "API Key")), request.get("description") == null ? null : String.valueOf(request.get("description")), request.get("allowedModels")));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiKeyEntity> update(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(apiKeyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        apiKeyService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
