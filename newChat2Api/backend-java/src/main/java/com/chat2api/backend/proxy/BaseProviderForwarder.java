package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class BaseProviderForwarder implements ProviderForwarder {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    protected BaseProviderForwarder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ForwardResult forward(ProviderEntity provider, AccountEntity account, Map<String, String> credentials, Map<String, Object> request, String actualModel) {
        try {
            Map<String, Object> outgoing = buildRequest(provider, account, credentials, request, actualModel);
            HttpHeaders headers = buildHeaders(provider, credentials);
            URI uri = URI.create(endpoint(provider, outgoing));
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(objectMapper.writeValueAsString(outgoing), headers), String.class);
            return ForwardResult.ok(response.getStatusCode().value(), response.getHeaders().getContentType() == null ? MediaType.APPLICATION_JSON_VALUE : response.getHeaders().getContentType().toString(), response.getBody());
        } catch (Exception error) {
            return ForwardResult.fail(502, error.getMessage());
        }
    }

    protected String endpoint(ProviderEntity provider) {
        return provider.getApiEndpoint() + provider.getChatPath();
    }

    protected String endpoint(ProviderEntity provider, Map<String, Object> request) {
        return endpoint(provider);
    }

    protected Map<String, Object> buildRequest(ProviderEntity provider, AccountEntity account, Map<String, String> credentials, Map<String, Object> request, String actualModel) {
        Map<String, Object> outgoing = new LinkedHashMap<>(request);
        outgoing.put("model", actualModel);
        return outgoing;
    }

    protected HttpHeaders buildHeaders(ProviderEntity provider, Map<String, String> credentials) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        provider.getHeaders().forEach((key, value) -> headers.set(String.valueOf(key), String.valueOf(value)));
        String token = first(credentials, "token", "accessToken", "access_token", "jwt", "serviceToken", "service_token", "refresh_token");
        String cookie = first(credentials, "cookie", "cookies");
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token.replaceFirst("(?i)^Bearer\\s+", ""));
        }
        if (cookie != null && !cookie.isBlank()) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }

    protected String first(Map<String, String> source, String... keys) {
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
}
