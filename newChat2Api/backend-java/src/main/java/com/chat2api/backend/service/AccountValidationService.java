package com.chat2api.backend.service;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.AccountStatus;
import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.proxy.DeepSeekProtocol;
import com.chat2api.backend.repository.ProviderRepository;
import com.chat2api.backend.support.DeepSeekCredentialSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountValidationService {
    private final AccountService accountService;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public AccountValidationService(AccountService accountService, ProviderRepository providerRepository, ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> validate(String accountId) {
        AccountEntity account = accountService.get(accountId);
        ProviderEntity provider = providerRepository.findById(account.getProviderId()).orElse(null);
        Map<String, String> credentials = accountService.credentials(accountId);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        validateAccount(account, errors);
        if (provider == null) {
            errors.add("provider_not_found");
        } else {
            validateProvider(provider, errors, warnings);
            validateCredentials(provider, credentials, errors, warnings);
        }
        boolean valid = errors.isEmpty();
        account.setStatus(valid ? AccountStatus.ACTIVE : AccountStatus.ERROR);
        account.setErrorMessage(valid ? null : String.join(",", errors));
        account.setUpdatedAt(Instant.now());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", accountId);
        result.put("providerId", account.getProviderId());
        result.put("valid", valid);
        result.put("status", account.getStatus().name().toLowerCase());
        result.put("errors", errors);
        result.put("warnings", warnings);
        return result;
    }

    private void validateAccount(AccountEntity account, List<String> errors) {
        if (isBlank(account.getName())) {
            errors.add("account_name_missing");
        }
    }

    private void validateProvider(ProviderEntity provider, List<String> errors, List<String> warnings) {
        if (!provider.isEnabled()) {
            errors.add("provider_disabled");
        }
        if (isBlank(provider.getVendor())) {
            errors.add("provider_vendor_missing");
        }
        if (isBlank(provider.getApiEndpoint())) {
            errors.add("provider_api_endpoint_missing");
        }
        if (isBlank(provider.getChatPath())) {
            warnings.add("provider_chat_path_missing");
        }
    }

    private void validateCredentials(ProviderEntity provider, Map<String, String> credentials, List<String> errors, List<String> warnings) {
        if (credentials == null || credentials.isEmpty()) {
            errors.add("credentials_missing");
            return;
        }
        String authType = provider.getAuthType() == null ? "" : provider.getAuthType();
        boolean hasToken = hasAny(credentials, "token", "accessToken", "access_token", "apiKey", "jwt", "refresh_token", "refreshToken");
        boolean hasCookie = hasAny(credentials, "cookie", "cookies");
        if ("deepseek".equals(provider.getVendor())) {
            hasToken = !DeepSeekCredentialSupport.token(credentials).isBlank();
        }
        if (authType.toLowerCase().contains("cookie") && !hasCookie) {
            errors.add("cookie_missing");
        }
        if ((authType.toLowerCase().contains("token") || authType.equalsIgnoreCase("jwt")) && !hasToken) {
            errors.add("token_missing");
        }
        if ("qwen-ai".equals(provider.getVendor()) && !hasCookie && !hasToken) {
            errors.add("qwen_ai_cookie_or_token_missing");
        }
        if ("zai".equals(provider.getVendor()) && !hasToken) {
            errors.add("zai_token_missing");
        }
        if ("deepseek".equals(provider.getVendor()) && !hasToken) {
            errors.add("deepseek_token_missing");
        }
        if ("deepseek".equals(provider.getVendor()) && hasToken) {
            validateDeepSeekCredentials(credentials, errors);
        }
        if (!hasToken && !hasCookie) {
            warnings.add("no_common_auth_fields_detected");
        }
    }

    private void validateDeepSeekCredentials(Map<String, String> credentials, List<String> errors) {
        try {
            String token = DeepSeekCredentialSupport.token(credentials);
            if (token.isBlank()) {
                errors.add("deepseek_token_missing");
                return;
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create("https://chat.deepseek.com/api/v0/users/current"),
                    HttpMethod.GET,
                    new HttpEntity<>(null, deepSeekHeaders(token, DeepSeekCredentialSupport.cookie(credentials))),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                errors.add("deepseek_validation_failed_http_" + response.getStatusCode().value());
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(response.getBody() == null ? "{}" : response.getBody(), new TypeReference<>() {});
            Object code = payload.get("code");
            Object bizData = nested(payload, "data", "biz_data");
            if ((code != null && !"0".equals(String.valueOf(code))) || !(bizData instanceof Map<?, ?>)) {
                errors.add("deepseek_token_invalid");
            }
        } catch (Exception error) {
            errors.add("deepseek_validation_failed");
        }
    }

    private boolean hasAny(Map<String, String> credentials, String... keys) {
        for (String key : keys) {
            String value = credentials.get(key);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private HttpHeaders deepSeekHeaders(String token, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        headers.setContentType(MediaType.APPLICATION_JSON);
        DeepSeekProtocol.applyCommonHeaders(headers, "https://chat.deepseek.com/");
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        if (cookie != null && !cookie.isBlank()) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return headers;
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

    private Object nested(Map<String, Object> source, String... keys) {
        Object current = source;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }
}
