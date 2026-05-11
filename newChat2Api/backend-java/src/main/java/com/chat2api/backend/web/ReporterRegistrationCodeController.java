package com.chat2api.backend.web;

import com.chat2api.backend.domain.ReporterRegistrationCodeEntity;
import com.chat2api.backend.service.ReporterRegistrationCodeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reporter-registration-codes")
public class ReporterRegistrationCodeController {
    private final ReporterRegistrationCodeService service;

    public ReporterRegistrationCodeController(ReporterRegistrationCodeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RegistrationCodeResponse>> list() {
        return ApiResponse.ok(service.list().stream().map(entity -> RegistrationCodeResponse.from(entity, service.decryptCode(entity))).toList());
    }

    @PostMapping
    public ApiResponse<CreatedRegistrationCodeResponse> create(@RequestBody Map<String, Object> request) {
        ReporterRegistrationCodeService.CreatedRegistrationCode created = service.create(
                String.valueOf(request.getOrDefault("name", "Reporter registration code")),
                request.get("description") == null ? null : String.valueOf(request.get("description")),
                request.get("code") == null ? null : String.valueOf(request.get("code"))
        );
        return ApiResponse.ok(CreatedRegistrationCodeResponse.from(created.entity(), created.code()));
    }

    @PutMapping("/{id}")
    public ApiResponse<RegistrationCodeResponse> update(@PathVariable String id, @RequestBody Map<String, Object> request) {
        ReporterRegistrationCodeEntity entity = service.update(id, request);
        return ApiResponse.ok(RegistrationCodeResponse.from(entity, service.decryptCode(entity)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    public record RegistrationCodeResponse(String id, String name, String description, boolean enabled, Instant createdAt, Instant updatedAt, String code) {
        static RegistrationCodeResponse from(ReporterRegistrationCodeEntity entity, String code) {
            return new RegistrationCodeResponse(entity.getId(), entity.getName(), entity.getDescription(), entity.isEnabled(), entity.getCreatedAt(), entity.getUpdatedAt(), code);
        }
    }

    public record CreatedRegistrationCodeResponse(String id, String name, String description, boolean enabled, Instant createdAt, Instant updatedAt, String code) {
        static CreatedRegistrationCodeResponse from(ReporterRegistrationCodeEntity entity, String code) {
            return new CreatedRegistrationCodeResponse(entity.getId(), entity.getName(), entity.getDescription(), entity.isEnabled(), entity.getCreatedAt(), entity.getUpdatedAt(), code);
        }
    }
}
