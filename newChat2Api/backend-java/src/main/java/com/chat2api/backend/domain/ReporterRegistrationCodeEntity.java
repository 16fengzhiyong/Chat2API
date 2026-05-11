package com.chat2api.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reporter_registration_codes")
public class ReporterRegistrationCodeEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 120)
    private String codeHash;
    @Column(length = 1024)
    private String encryptedCode;
    private String description;
    private boolean enabled = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public String getEncryptedCode() { return encryptedCode; }
    public void setEncryptedCode(String encryptedCode) { this.encryptedCode = encryptedCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
