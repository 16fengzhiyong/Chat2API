package com.chat2api.backend.web;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.service.AccountService;
import com.chat2api.backend.service.AccountValidationService;
import com.chat2api.backend.service.RedactionService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;
    private final AccountValidationService accountValidationService;
    private final RedactionService redactionService;

    public AccountController(AccountService accountService, AccountValidationService accountValidationService, RedactionService redactionService) {
        this.accountService = accountService;
        this.accountValidationService = accountValidationService;
        this.redactionService = redactionService;
    }

    @GetMapping
    public ApiResponse<List<AccountEntity>> list() {
        return ApiResponse.ok(accountService.list());
    }

    @GetMapping("/{id}/credentials")
    public ApiResponse<Map<String, String>> maskedCredentials(@PathVariable String id) {
        return ApiResponse.ok(redactionService.maskCredentials(accountService.credentials(id)));
    }

    @PostMapping
    public ApiResponse<AccountEntity> create(@RequestBody Map<String, Object> request) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account creation is disabled in admin console; use reporter client upload instead");
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountEntity> update(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(accountService.update(id, request));
    }

    @PostMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable String id) {
        return ApiResponse.ok(accountValidationService.validate(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        accountService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    private Map<String, String> castStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        map.forEach((key, val) -> result.put(String.valueOf(key), val == null ? null : String.valueOf(val)));
        return result;
    }
}
