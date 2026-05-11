package com.chat2api.backend.domain;

import com.chat2api.backend.persistence.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {
    @Id
    private String id;
    private String name;
    private String keyValue;
    private boolean enabled = true;
    private long usageCount;
    private String description;
    @Column(columnDefinition = "json")
    @Convert(converter = StringListConverter.class)
    private List<String> allowedModels = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant lastUsedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getUsageCount() { return usageCount; }
    public void setUsageCount(long usageCount) { this.usageCount = usageCount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getAllowedModels() { return allowedModels; }
    public void setAllowedModels(List<String> allowedModels) { this.allowedModels = allowedModels; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
