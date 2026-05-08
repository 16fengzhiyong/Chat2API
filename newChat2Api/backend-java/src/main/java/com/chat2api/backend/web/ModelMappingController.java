package com.chat2api.backend.web;

import com.chat2api.backend.domain.ModelMappingEntity;
import com.chat2api.backend.repository.ModelMappingRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model-mappings")
public class ModelMappingController {
    private final ModelMappingRepository modelMappingRepository;

    public ModelMappingController(ModelMappingRepository modelMappingRepository) {
        this.modelMappingRepository = modelMappingRepository;
    }

    @GetMapping
    public ApiResponse<List<ModelMappingEntity>> list() {
        return ApiResponse.ok(modelMappingRepository.findAll());
    }

    @PostMapping
    public ApiResponse<ModelMappingEntity> save(@RequestBody ModelMappingEntity mapping) {
        return ApiResponse.ok(modelMappingRepository.save(mapping));
    }

    @DeleteMapping("/{model}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String model) {
        modelMappingRepository.deleteById(model);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
