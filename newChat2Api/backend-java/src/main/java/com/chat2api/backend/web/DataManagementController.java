package com.chat2api.backend.web;

import com.chat2api.backend.service.DataManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/data")
public class DataManagementController {
    private final DataManagementService dataManagementService;

    public DataManagementController(DataManagementService dataManagementService) {
        this.dataManagementService = dataManagementService;
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> exportData() {
        return ApiResponse.ok(dataManagementService.exportData());
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importData(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(dataManagementService.importData(payload));
    }
}
