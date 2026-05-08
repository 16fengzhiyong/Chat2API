package com.chat2api.backend.domain;

import com.chat2api.backend.persistence.JsonMapConverter;
import com.chat2api.backend.persistence.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "providers")
public class ProviderEntity {
    @Id
    private String id;
    private String name;
    @Enumerated(EnumType.STRING)
    private ProviderType type;
    private String vendor;
    private String authType;
    private String apiEndpoint;
    private String chatPath;
    @Column(columnDefinition = "json")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> headers = new LinkedHashMap<>();
    private boolean enabled = true;
    @Column(length = 1000)
    private String description;
    @Column(columnDefinition = "json")
    @Convert(converter = StringListConverter.class)
    private List<String> supportedModels = new ArrayList<>();
    @Column(columnDefinition = "json")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> modelMappings = new LinkedHashMap<>();
    @Column(columnDefinition = "json")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> credentialFields = new LinkedHashMap<>();
    private String status = "unknown";
    private Instant lastStatusCheck;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ProviderType getType() { return type; }
    public void setType(ProviderType type) { this.type = type; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }
    public String getChatPath() { return chatPath; }
    public void setChatPath(String chatPath) { this.chatPath = chatPath; }
    public Map<String, Object> getHeaders() { return headers; }
    public void setHeaders(Map<String, Object> headers) { this.headers = headers; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getSupportedModels() { return supportedModels; }
    public void setSupportedModels(List<String> supportedModels) { this.supportedModels = supportedModels; }
    public Map<String, Object> getModelMappings() { return modelMappings; }
    public void setModelMappings(Map<String, Object> modelMappings) { this.modelMappings = modelMappings; }
    public Map<String, Object> getCredentialFields() { return credentialFields; }
    public void setCredentialFields(Map<String, Object> credentialFields) { this.credentialFields = credentialFields; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastStatusCheck() { return lastStatusCheck; }
    public void setLastStatusCheck(Instant lastStatusCheck) { this.lastStatusCheck = lastStatusCheck; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
