package com.chat2api.backend.web;

import com.chat2api.backend.domain.SessionEntity;
import com.chat2api.backend.repository.SessionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionRepository sessionRepository;

    public SessionController(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public ApiResponse<List<SessionEntity>> list() {
        return ApiResponse.ok(sessionRepository.findAll());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        sessionRepository.deleteById(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
