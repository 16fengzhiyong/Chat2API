package com.chat2api.backend.web;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ReporterClientEntity;
import com.chat2api.backend.repository.ProviderRepository;
import com.chat2api.backend.repository.ReporterClientRepository;
import com.chat2api.backend.service.AccountService;
import com.chat2api.backend.service.AccountValidationService;
import com.chat2api.backend.service.IdService;
import com.chat2api.backend.service.ReporterRegistrationCodeService;
import com.chat2api.backend.support.DeepSeekCredentialSupport;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reporter")
public class ReporterController {
    private final ReporterClientRepository reporterClientRepository;
    private final ProviderRepository providerRepository;
    private final AccountService accountService;
    private final AccountValidationService accountValidationService;
    private final IdService idService;
    private final ReporterRegistrationCodeService registrationCodeService;

    public ReporterController(ReporterClientRepository reporterClientRepository, ProviderRepository providerRepository, AccountService accountService, AccountValidationService accountValidationService, IdService idService, ReporterRegistrationCodeService registrationCodeService) {
        this.reporterClientRepository = reporterClientRepository;
        this.providerRepository = providerRepository;
        this.accountService = accountService;
        this.accountValidationService = accountValidationService;
        this.idService = idService;
        this.registrationCodeService = registrationCodeService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        String registrationCode = String.valueOf(request.getOrDefault("registrationCode", ""));
        ReporterRegistrationCodeService.VerificationResult verification = registrationCodeService.verifyWithReason(registrationCode);
        if (!verification.valid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, verification.message());
        }
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
    public ApiResponse<Map<String, Object>> heartbeat(@RequestHeader(value = "X-Reporter-Id", required = false) String clientId, @RequestHeader(value = "X-Reporter-Secret", required = false) String secret) {
        ReporterClientEntity client = requireClient(clientId, secret);
        client.setStatus("online");
        client.setLastHeartbeatAt(Instant.now());
        reporterClientRepository.save(client);
        return ApiResponse.ok(Map.of("status", "ok", "serverTime", Instant.now().toString()));
    }

    @PostMapping("/accounts")
    public ApiResponse<AccountEntity> uploadAccount(@RequestHeader(value = "X-Reporter-Id", required = false) String clientId, @RequestHeader(value = "X-Reporter-Secret", required = false) String secret, @RequestBody Map<String, Object> request) {
        requireClient(clientId, secret);
        String providerId = String.valueOf(request.get("providerId"));
        if (providerId.isBlank() || !providerRepository.existsById(providerId)) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }
        String name = String.valueOf(request.getOrDefault("name", "")).trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Account name is required");
        }
        Map<String, String> credentials = normalizeCredentials(providerId, castStringMap(request.get("credentials")));
        AccountEntity account = accountService.create(
                providerId,
                name,
                request.get("email") == null ? null : String.valueOf(request.get("email")),
                credentials,
                request.get("dailyLimit") == null ? null : Long.valueOf(String.valueOf(request.get("dailyLimit")))
        );
        accountValidationService.validate(account.getId());
        return ApiResponse.ok(accountService.get(account.getId()));
    }

    private ReporterClientEntity requireClient(String clientId, String secret) {
        if (clientId == null || clientId.isBlank() || secret == null || secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid reporter credentials");
        }
        ReporterClientEntity client = reporterClientRepository.findByIdAndSecret(clientId, secret).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid reporter credentials"));
        if (!"online".equalsIgnoreCase(client.getStatus()) && !"offline".equalsIgnoreCase(client.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reporter client is disabled");
        }
        return client;
    }

    private Map<String, String> castStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        map.forEach((key, val) -> result.put(String.valueOf(key), val == null ? null : String.valueOf(val)));
        return result;
    }

    private Map<String, String> normalizeCredentials(String providerId, Map<String, String> credentials) {
        Map<String, String> base = new LinkedHashMap<>();
        if (credentials != null) {
            credentials.forEach((key, value) -> {
                String text = value == null ? "" : value.trim();
                if (!text.isBlank()) {
                    base.put(key, text);
                }
            });
        }
        if (!"deepseek".equals(providerId)) {
            return base;
        }
        return DeepSeekCredentialSupport.normalize(base);
    }

    private String stripBearerPrefix(String value) {
        return value == null ? "" : value.trim().replaceFirst("(?i)^Bearer\\s+", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
