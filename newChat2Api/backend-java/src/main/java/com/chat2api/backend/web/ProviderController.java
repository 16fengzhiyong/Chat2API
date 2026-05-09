package com.chat2api.backend.web;

import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.repository.ProviderRepository;
import com.chat2api.backend.service.ProviderMaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderRepository providerRepository;
    private final ProviderMaintenanceService providerMaintenanceService;

    public ProviderController(ProviderRepository providerRepository, ProviderMaintenanceService providerMaintenanceService) {
        this.providerRepository = providerRepository;
        this.providerMaintenanceService = providerMaintenanceService;
    }

    @GetMapping
    public ApiResponse<List<ProviderEntity>> list() {
        return ApiResponse.ok(providerRepository.findAll());
    }

    @PostMapping
    public ApiResponse<ProviderEntity> create(@RequestBody ProviderEntity provider) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Provider creation is disabled in admin console");
    }

    @PutMapping("/{id}")
    public ApiResponse<ProviderEntity> update(@PathVariable String id, @RequestBody ProviderEntity input) {
        ProviderEntity provider = providerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
        provider.setName(input.getName());
        provider.setVendor(input.getVendor());
        provider.setAuthType(input.getAuthType());
        provider.setApiEndpoint(input.getApiEndpoint());
        provider.setChatPath(input.getChatPath());
        provider.setHeaders(input.getHeaders() == null ? new LinkedHashMap<>() : input.getHeaders());
        provider.setEnabled(input.isEnabled());
        provider.setDescription(input.getDescription());
        provider.setSupportedModels(input.getSupportedModels());
        provider.setModelMappings(input.getModelMappings() == null ? new LinkedHashMap<>() : input.getModelMappings());
        provider.setCredentialFields(input.getCredentialFields() == null ? new LinkedHashMap<>() : input.getCredentialFields());
        provider.setUpdatedAt(Instant.now());
        return ApiResponse.ok(providerRepository.save(provider));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> checkStatus(@PathVariable String id) {
        ProviderEntity provider = providerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
        provider.setStatus(provider.isEnabled() ? "active" : "disabled");
        provider.setLastStatusCheck(Instant.now());
        providerRepository.save(provider);
        return ApiResponse.ok(Map.of("id", id, "status", provider.getStatus(), "checkedAt", provider.getLastStatusCheck().toString()));
    }

    @PostMapping("/{id}/models/refresh")
    public ApiResponse<ProviderEntity> refreshModels(@PathVariable String id) {
        return ApiResponse.ok(providerMaintenanceService.refreshModels(id));
    }

    @PostMapping("/{id}/clear-chats")
    public ApiResponse<Map<String, Object>> clearChats(@PathVariable String id) {
        return ApiResponse.ok(providerMaintenanceService.clearChats(id));
    }

    @GetMapping("/{id}/credits")
    public ApiResponse<Map<String, Object>> credits(@PathVariable String id) {
        return ApiResponse.ok(providerMaintenanceService.credits(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        providerRepository.deleteById(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
