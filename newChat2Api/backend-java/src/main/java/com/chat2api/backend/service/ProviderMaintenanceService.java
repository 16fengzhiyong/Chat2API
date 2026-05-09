package com.chat2api.backend.service;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.repository.ProviderRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProviderMaintenanceService {
    private final ProviderRepository providerRepository;
    private final AccountService accountService;
    private final RestTemplate restTemplate = new RestTemplate();

    public ProviderMaintenanceService(ProviderRepository providerRepository, AccountService accountService) {
        this.providerRepository = providerRepository;
        this.accountService = accountService;
    }

    public ProviderEntity refreshModels(String providerId) {
        ProviderEntity provider = provider(providerId);
        provider.setLastStatusCheck(Instant.now());
        provider.setUpdatedAt(Instant.now());
        return providerRepository.save(provider);
    }

    public Map<String, Object> clearChats(String providerId) {
        ProviderEntity provider = provider(providerId);
        List<Map<String, Object>> results = new ArrayList<>();
        boolean allOk = true;
        for (AccountEntity account : accountService.listByProvider(providerId)) {
            Map<String, Object> result = clearChats(provider, account, accountService.credentials(account.getId()));
            results.add(result);
            allOk = allOk && Boolean.TRUE.equals(result.get("success"));
        }
        return Map.of("providerId", providerId, "success", allOk, "accounts", results);
    }

    public Map<String, Object> credits(String providerId) {
        ProviderEntity provider = provider(providerId);
        List<Map<String, Object>> results = new ArrayList<>();
        for (AccountEntity account : accountService.listByProvider(providerId)) {
            results.add(credits(provider, account, accountService.credentials(account.getId())));
        }
        return Map.of("providerId", providerId, "accounts", results);
    }

    private Map<String, Object> clearChats(ProviderEntity provider, AccountEntity account, Map<String, String> credentials) {
        return switch (provider.getVendor()) {
            case "qwen-ai" -> hasAny(credentials, "cookies", "cookie", "token", "accessToken", "apiKey") ? delete(account, "https://chat.qwen.ai/api/v2/chats/", qwenAiHeaders(credentials, null)) : failed(account, "missing_credentials");
            case "zai" -> hasAny(credentials, "token", "accessToken", "access_token") ? delete(account, "https://chat.z.ai/api/v1/chats/", bearerHeaders(credentials, "token", "accessToken", "access_token")) : failed(account, "missing_credentials");
            default -> unsupported(account, "clear_chats_not_supported_for_" + provider.getVendor());
        };
    }

    private Map<String, Object> credits(ProviderEntity provider, AccountEntity account, Map<String, String> credentials) {
        return unsupported(account, "credits_not_supported_for_" + provider.getVendor());
    }

    private Map<String, Object> delete(AccountEntity account, String url, HttpHeaders headers) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(URI.create(url), HttpMethod.DELETE, new HttpEntity<>(null, headers), String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accountId", account.getId());
            result.put("success", success);
            result.put("status", response.getStatusCode().value());
            result.put("body", response.getBody() == null ? "" : response.getBody());
            return result;
        } catch (Exception error) {
            return failed(account, error.getMessage());
        }
    }

    private HttpHeaders qwenAiHeaders(Map<String, String> credentials, String chatId) {
        HttpHeaders headers = browserHeaders("https://chat.qwen.ai", chatId == null ? "https://chat.qwen.ai/" : "https://chat.qwen.ai/c/" + chatId);
        String cookie = first(credentials, "cookies", "cookie");
        String token = first(credentials, "token", "accessToken", "apiKey");
        if (cookie != null && !cookie.isBlank()) {
            headers.set(HttpHeaders.COOKIE, cookie);
        } else if (token != null && !token.isBlank()) {
            headers.set(HttpHeaders.COOKIE, "token=" + token);
        }
        headers.set("source", "web");
        headers.set("Version", "0.2.45");
        return headers;
    }

    private HttpHeaders bearerHeaders(Map<String, String> credentials, String... keys) {
        HttpHeaders headers = browserHeaders("https://chat.z.ai", "https://chat.z.ai/");
        String token = first(credentials, keys);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token.replaceFirst("(?i)^Bearer\\s+", ""));
        }
        return headers;
    }

    private HttpHeaders browserHeaders(String origin, String referer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setOrigin(origin);
        headers.set(HttpHeaders.REFERER, referer);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        return headers;
    }

    private Map<String, Object> unsupported(AccountEntity account, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", account.getId());
        result.put("success", false);
        result.put("unsupported", true);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> failed(AccountEntity account, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", account.getId());
        result.put("success", false);
        result.put("reason", reason == null ? "request_failed" : reason);
        return result;
    }

    private ProviderEntity provider(String id) {
        return providerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
    }

    private String first(Map<String, String> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            String value = source.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean hasAny(Map<String, String> source, String... keys) {
        return first(source, keys) != null;
    }
}
