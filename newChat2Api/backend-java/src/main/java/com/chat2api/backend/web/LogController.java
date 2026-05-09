package com.chat2api.backend.web;

import com.chat2api.backend.domain.RequestLogEntity;
import com.chat2api.backend.service.RequestLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final RequestLogService requestLogService;

    public LogController(RequestLogService requestLogService) {
        this.requestLogService = requestLogService;
    }

    @GetMapping("/requests")
    public ApiResponse<List<RequestLogEntity>> requestLogs() {
        return ApiResponse.ok(requestLogService.list());
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics() {
        return ApiResponse.ok(requestLogService.statistics());
    }

    @GetMapping("/statistics/daily")
    public ApiResponse<List<Map<String, Object>>> dailyStatistics() {
        return ApiResponse.ok(requestLogService.dailyStatistics());
    }
}
