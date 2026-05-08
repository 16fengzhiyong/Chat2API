package com.chat2api.backend.service;

import com.chat2api.backend.domain.AccountCredentialEntity;
import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.AccountStatus;
import com.chat2api.backend.repository.AccountCredentialRepository;
import com.chat2api.backend.repository.AccountRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {
    private static final TypeReference<Map<String, String>> CREDENTIAL_TYPE = new TypeReference<>() {};
    private final AccountRepository accountRepository;
    private final AccountCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final IdService idService;
    private final ObjectMapper objectMapper;

    public AccountService(AccountRepository accountRepository, AccountCredentialRepository credentialRepository, EncryptionService encryptionService, IdService idService, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.idService = idService;
        this.objectMapper = objectMapper;
    }

    public List<AccountEntity> list() {
        return accountRepository.findAll();
    }

    public List<AccountEntity> listByProvider(String providerId) {
        return accountRepository.findByProviderId(providerId);
    }

    public AccountEntity get(String id) {
        return accountRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    @Transactional
    public AccountEntity create(String providerId, String name, String email, Map<String, String> credentials, Long dailyLimit) {
        AccountEntity account = new AccountEntity();
        account.setId(idService.id("acct"));
        account.setProviderId(providerId);
        account.setName(name);
        account.setEmail(email);
        account.setDailyLimit(dailyLimit);
        account.setStatus(AccountStatus.ACTIVE);
        account.setLastUsed(Instant.now());
        AccountEntity saved = accountRepository.save(account);
        saveCredentials(saved.getId(), credentials);
        return saved;
    }

    @Transactional
    public AccountEntity update(String id, Map<String, Object> updates) {
        AccountEntity account = get(id);
        if (updates.containsKey("name")) account.setName((String) updates.get("name"));
        if (updates.containsKey("email")) account.setEmail((String) updates.get("email"));
        if (updates.containsKey("status")) account.setStatus(AccountStatus.valueOf(String.valueOf(updates.get("status")).toUpperCase()));
        if (updates.containsKey("dailyLimit")) account.setDailyLimit(updates.get("dailyLimit") == null ? null : Long.valueOf(String.valueOf(updates.get("dailyLimit"))));
        if (updates.containsKey("credentials")) saveCredentials(id, castCredentials(updates.get("credentials")));
        account.setUpdatedAt(Instant.now());
        return accountRepository.save(account);
    }

    @Transactional
    public void delete(String id) {
        credentialRepository.deleteById(id);
        accountRepository.deleteById(id);
    }

    public Map<String, String> credentials(String accountId) {
        return credentialRepository.findById(accountId)
                .map(entity -> {
                    try {
                        return objectMapper.readValue(encryptionService.decrypt(entity.getEncryptedCredentials()), CREDENTIAL_TYPE);
                    } catch (Exception error) {
                        throw new IllegalStateException("Failed to read credentials", error);
                    }
                })
                .orElseGet(LinkedHashMap::new);
    }

    @Transactional
    public void touchSuccess(String accountId) {
        AccountEntity account = get(accountId);
        account.setRequestCount(account.getRequestCount() + 1);
        account.setTodayUsed(account.getTodayUsed() + 1);
        account.setLastUsed(Instant.now());
        accountRepository.save(account);
    }

    public boolean available(AccountEntity account) {
        return account.getStatus() == AccountStatus.ACTIVE && (account.getDailyLimit() == null || account.getTodayUsed() < account.getDailyLimit());
    }

    private void saveCredentials(String accountId, Map<String, String> credentials) {
        try {
            AccountCredentialEntity entity = new AccountCredentialEntity();
            entity.setAccountId(accountId);
            entity.setEncryptedCredentials(encryptionService.encrypt(objectMapper.writeValueAsString(credentials == null ? new LinkedHashMap<>() : credentials)));
            entity.setUpdatedAt(Instant.now());
            credentialRepository.save(entity);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to save credentials", error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> castCredentials(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((key, val) -> result.put(String.valueOf(key), val == null ? null : String.valueOf(val)));
            return result;
        }
        return new LinkedHashMap<>();
    }
}
