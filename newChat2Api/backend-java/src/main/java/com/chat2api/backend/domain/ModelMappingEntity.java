package com.chat2api.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "model_mappings")
public class ModelMappingEntity {
    @Id
    private String requestModel;
    private String actualModel;
    private String preferredProviderId;
    private String preferredAccountId;

    public String getRequestModel() { return requestModel; }
    public void setRequestModel(String requestModel) { this.requestModel = requestModel; }
    public String getActualModel() { return actualModel; }
    public void setActualModel(String actualModel) { this.actualModel = actualModel; }
    public String getPreferredProviderId() { return preferredProviderId; }
    public void setPreferredProviderId(String preferredProviderId) { this.preferredProviderId = preferredProviderId; }
    public String getPreferredAccountId() { return preferredAccountId; }
    public void setPreferredAccountId(String preferredAccountId) { this.preferredAccountId = preferredAccountId; }
}
