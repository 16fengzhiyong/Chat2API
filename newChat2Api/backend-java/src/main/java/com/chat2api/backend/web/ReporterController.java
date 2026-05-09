package com.chat2api.backend.web;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ReporterClientEntity;
import com.chat2api.backend.repository.ProviderRepository;
import com.chat2api.backend.repository.ReporterClientRepository;
import com.chat2api.backend.service.AccountService;
import com.chat2api.backend.service.IdService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/reporter")
public class ReporterController {
    private final ReporterClientRepository reporterClientRepository;
    private final ProviderRepository providerRepository;
    private final AccountService accountService;
    private final IdService idService;

    public ReporterController(ReporterClientRepository reporterClientRepository, ProviderRepository providerRepository, AccountService accountService, IdService idService) {
        this.reporterClientRepository = reporterClientRepository;
        this.providerRepository = providerRepository;
        this.accountService = accountService;
        this.idService = idService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        ReporterClientEntity client = new ReporterClientEntity();
        client.setId(idService.id("reporter"));
        client.setName(String.valueOf(request.getOrDefault("name", "Reporter Client")));
        client.setVersion(String.valueOf(request.getOrDefault("version", "unknown")));
        client.setSecret(idService.secret("rpt"));
        client.setStatus("online");
        client.setLastHeartbeatAt(Instant.now());
        reporterClientRepository.save(client);
        return ApiResponse.ok(Map.of("clientId", client.getId(), "secret", client.getSecret()));
    }

    @PostMapping("/heartbeat")
    public ApiResponse<Map<String, Object>> heartbeat(@RequestHeader("X-Reporter-Id") String clientId, @RequestHeader("X-Reporter-Secret") String secret) {
        ReporterClientEntity client = requireClient(clientId, secret);
        client.setStatus("online");
        client.setLastHeartbeatAt(Instant.now());
        reporterClientRepository.save(client);
        return ApiResponse.ok(Map.of("status", "ok", "serverTime", Instant.now().toString()));
    }

    @PostMapping("/accounts")
    public ApiResponse<AccountEntity> uploadAccount(@RequestHeader("X-Reporter-Id") String clientId, @RequestHeader("X-Reporter-Secret") String secret, @RequestBody Map<String, Object> request) {
        requireClient(clientId, secret);
        String providerId = String.valueOf(request.get("providerId"));
        if (providerId.isBlank() || !providerRepository.existsById(providerId)) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }
        AccountEntity account = accountService.create(
                providerId,
                String.valueOf(request.getOrDefault("name", "Uploaded Account")),
                request.get("email") == null ? null : String.valueOf(request.get("email")),
                castStringMap(request.get("credentials")),
                request.get("dailyLimit") == null ? null : Long.valueOf(String.valueOf(request.get("dailyLimit")))
        );
        return ApiResponse.ok(account);
    }

    private ReporterClientEntity requireClient(String clientId, String secret) {
        return reporterClientRepository.findByIdAndSecret(clientId, secret).orElseThrow(() -> new IllegalArgumentException("Invalid reporter credentials"));
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
