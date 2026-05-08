package com.chat2api.backend.web;

import com.chat2api.backend.domain.SystemPromptEntity;
import com.chat2api.backend.repository.SystemPromptRepository;
import com.chat2api.backend.service.IdService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-prompts")
public class SystemPromptController {
    private final SystemPromptRepository systemPromptRepository;
    private final IdService idService;

    public SystemPromptController(SystemPromptRepository systemPromptRepository, IdService idService) {
        this.systemPromptRepository = systemPromptRepository;
        this.idService = idService;
    }

    @GetMapping
    public ApiResponse<List<SystemPromptEntity>> list() {
        return ApiResponse.ok(systemPromptRepository.findAll());
    }

    @PostMapping
    public ApiResponse<SystemPromptEntity> save(@RequestBody SystemPromptEntity prompt) {
        if (prompt.getId() == null || prompt.getId().isBlank()) {
            prompt.setId(idService.id("prompt"));
            prompt.setCreatedAt(Instant.now());
        }
        prompt.setUpdatedAt(Instant.now());
        return ApiResponse.ok(systemPromptRepository.save(prompt));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        systemPromptRepository.deleteById(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
