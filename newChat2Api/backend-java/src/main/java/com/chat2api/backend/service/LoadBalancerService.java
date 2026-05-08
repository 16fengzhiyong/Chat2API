package com.chat2api.backend.service;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.repository.AccountRepository;
import com.chat2api.backend.repository.ModelMappingRepository;
import com.chat2api.backend.repository.ProviderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LoadBalancerService {
    private final ProviderRepository providerRepository;
    private final AccountRepository accountRepository;
    private final ModelMappingRepository modelMappingRepository;
    private final AccountService accountService;
    private final Map<String, Integer> roundRobinIndex = new HashMap<>();

    public LoadBalancerService(ProviderRepository providerRepository, AccountRepository accountRepository, ModelMappingRepository modelMappingRepository, AccountService accountService) {
        this.providerRepository = providerRepository;
        this.accountRepository = accountRepository;
        this.modelMappingRepository = modelMappingRepository;
        this.accountService = accountService;
    }

    public Optional<Selection> select(String requestedModel) {
        String preferredProviderId = modelMappingRepository.findById(requestedModel).map(mapping -> mapping.getPreferredProviderId()).orElse(null);
        String preferredAccountId = modelMappingRepository.findById(requestedModel).map(mapping -> mapping.getPreferredAccountId()).orElse(null);
        List<Selection> candidates = providerRepository.findByEnabledTrue().stream()
                .filter(provider -> preferredProviderId == null || provider.getId().equals(preferredProviderId))
                .filter(provider -> supports(provider, requestedModel))
                .flatMap(provider -> accountRepository.findByProviderId(provider.getId()).stream()
                        .filter(accountService::available)
                        .map(account -> new Selection(provider, account, mapModel(provider, requestedModel))))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (preferredAccountId != null) {
            Optional<Selection> preferred = candidates.stream().filter(selection -> selection.account().getId().equals(preferredAccountId)).findFirst();
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        String key = candidates.stream().map(selection -> selection.provider().getId()).sorted().reduce("", (left, right) -> left + "," + right);
        int index = roundRobinIndex.getOrDefault(key, 0);
        roundRobinIndex.put(key, (index + 1) % candidates.size());
        return Optional.of(candidates.stream().sorted(Comparator.comparing(selection -> selection.account().getLastUsed(), Comparator.nullsFirst(Comparator.naturalOrder()))).toList().get(index % candidates.size()));
    }

    private boolean supports(ProviderEntity provider, String model) {
        if (provider.getSupportedModels() == null || provider.getSupportedModels().isEmpty()) {
            return true;
        }
        String normalized = model.toLowerCase();
        return provider.getSupportedModels().stream().anyMatch(supported -> {
            String item = supported.toLowerCase();
            if (item.endsWith("*")) {
                return normalized.startsWith(item.substring(0, item.length() - 1));
            }
            return item.equals(normalized);
        }) || provider.getModelMappings().containsKey(model);
    }

    private String mapModel(ProviderEntity provider, String model) {
        return modelMappingRepository.findById(model).map(mapping -> mapping.getActualModel()).orElseGet(() -> {
            Object providerMapping = provider.getModelMappings().get(model);
            return providerMapping == null ? model : String.valueOf(providerMapping);
        });
    }

    public record Selection(ProviderEntity provider, AccountEntity account, String actualModel) {
    }
}
