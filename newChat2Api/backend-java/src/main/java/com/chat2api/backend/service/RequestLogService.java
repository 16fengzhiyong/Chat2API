package com.chat2api.backend.service;

import com.chat2api.backend.domain.RequestLogEntity;
import com.chat2api.backend.repository.RequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

    public List<Map<String, Object>> dailyStatistics() {
        List<RequestLogEntity> logs = requestLogRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long total = logs.stream().filter(log -> log.getTimestamp() != null && log.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate().equals(date)).count();
            long success = logs.stream().filter(log -> log.getTimestamp() != null && log.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate().equals(date) && "success".equalsIgnoreCase(log.getStatus())).count();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date.toString());
            day.put("total", total);
            day.put("success", success);
            day.put("failed", total - success);
            result.add(day);
        }
        return result;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return String.valueOf(value);
        }
    }
}
