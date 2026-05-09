package com.chat2api.backend.service;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.repository.AccountRepository;
import com.chat2api.backend.repository.AppConfigRepository;
import com.chat2api.backend.repository.ModelMappingRepository;
import com.chat2api.backend.repository.ProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

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
    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, Integer> roundRobinIndex = new HashMap<>();

    public LoadBalancerService(ProviderRepository providerRepository, AccountRepository accountRepository, ModelMappingRepository modelMappingRepository, AccountService accountService, AppConfigRepository appConfigRepository, ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.accountRepository = accountRepository;
        this.modelMappingRepository = modelMappingRepository;
        this.accountService = accountService;
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
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
        Map<String, Object> config = loadConfig();
        String strategy = String.valueOf(config.getOrDefault("strategy", "round_robin"));
        return switch (strategy) {
            case "fill_first" -> selectFillFirst(candidates);
            case "failover" -> selectFailover(candidates);
            default -> selectRoundRobin(candidates);
        };
    }

    private Optional<Selection> selectRoundRobin(List<Selection> candidates) {
        String key = candidates.stream().map(s -> s.provider().getId()).sorted().reduce("", (a, b) -> a + "," + b);
        int index = roundRobinIndex.getOrDefault(key, 0);
        roundRobinIndex.put(key, (index + 1) % candidates.size());
        return Optional.of(candidates.get(index % candidates.size()));
    }

    private Optional<Selection> selectFillFirst(List<Selection> candidates) {
        return candidates.stream()
                .min(Comparator.comparing(s -> s.account().getLastUsed(), Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private Optional<Selection> selectFailover(List<Selection> candidates) {
        return candidates.stream().findFirst();
    }

    private Map<String, Object> loadConfig() {
        return appConfigRepository.findById("loadBalance")
                .map(e -> {
                    try {
                        return objectMapper.readValue(e.getConfigValue(), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception ex) {
                        return new HashMap<String, Object>();
                    }
                })
                .orElseGet(HashMap::new);
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
