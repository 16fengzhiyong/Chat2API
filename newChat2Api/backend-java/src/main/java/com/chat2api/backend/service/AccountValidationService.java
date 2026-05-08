package com.chat2api.backend.service;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.AccountStatus;
import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountValidationService {
    private final AccountService accountService;
    private final ProviderRepository providerRepository;

    public AccountValidationService(AccountService accountService, ProviderRepository providerRepository) {
        this.accountService = accountService;
        this.providerRepository = providerRepository;
    }

    @Transactional
    public Map<String, Object> validate(String accountId) {
        AccountEntity account = accountService.get(accountId);
        ProviderEntity provider = providerRepository.findById(account.getProviderId()).orElse(null);
        Map<String, String> credentials = accountService.credentials(accountId);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
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
        if (!hasToken && !hasCookie) {
            warnings.add("no_common_auth_fields_detected");
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
}
