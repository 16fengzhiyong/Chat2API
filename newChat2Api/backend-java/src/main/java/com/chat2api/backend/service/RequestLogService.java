package com.chat2api.backend.service;

import com.chat2api.backend.domain.RequestLogEntity;
import com.chat2api.backend.repository.RequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RequestLogService {
    private final RequestLogRepository requestLogRepository;
    private final IdService idService;
    private final RedactionService redactionService;
    private final ObjectMapper objectMapper;

    public RequestLogService(RequestLogRepository requestLogRepository, IdService idService, RedactionService redactionService, ObjectMapper objectMapper) {
        this.requestLogRepository = requestLogRepository;
        this.idService = idService;
        this.redactionService = redactionService;
        this.objectMapper = objectMapper;
    }

    public List<RequestLogEntity> list() {
        return requestLogRepository.findAll();
    }

    public RequestLogEntity save(RequestLogEntity log) {
        if (log.getId() == null) {
            log.setId(idService.id("req"));
        }
        log.setRequestBody(redactionService.redactText(log.getRequestBody()));
        log.setResponseBody(redactionService.redactText(log.getResponseBody()));
        log.setErrorMessage(redactionService.redactText(log.getErrorMessage()));
        return requestLogRepository.save(log);
    }

    public Map<String, Object> statistics() {
        List<RequestLogEntity> logs = requestLogRepository.findAll();
        long success = logs.stream().filter(log -> "success".equalsIgnoreCase(log.getStatus())).count();
        long failed = logs.size() - success;
        double avgLatency = logs.stream().mapToLong(RequestLogEntity::getLatency).average().orElse(0);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRequests", logs.size());
        stats.put("successRequests", success);
        stats.put("failedRequests", failed);
        stats.put("averageLatency", avgLatency);
        return stats;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return String.valueOf(value);
        }
    }
}
