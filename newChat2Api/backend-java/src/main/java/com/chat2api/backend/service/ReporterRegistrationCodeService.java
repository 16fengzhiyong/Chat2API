package com.chat2api.backend.service;

import com.chat2api.backend.domain.ReporterRegistrationCodeEntity;
import com.chat2api.backend.repository.ReporterRegistrationCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ReporterRegistrationCodeService {
    private final ReporterRegistrationCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final IdService idService;
    private final EncryptionService encryptionService;

    public ReporterRegistrationCodeService(ReporterRegistrationCodeRepository repository, PasswordEncoder passwordEncoder, IdService idService, EncryptionService encryptionService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.idService = idService;
        this.encryptionService = encryptionService;
    }

    public List<ReporterRegistrationCodeEntity> list() {
        return repository.findAll();
    }

    public CreatedRegistrationCode create(String name, String description, String code) {
        String plainCode = code == null || code.isBlank() ? idService.secret("rrc") : code.trim();
        validateCode(plainCode);
        ReporterRegistrationCodeEntity entity = new ReporterRegistrationCodeEntity();
        entity.setId(idService.id("rrc"));
        entity.setName(normalizeName(name));
        entity.setDescription(blankToNull(description));
        entity.setCodeHash(passwordEncoder.encode(plainCode));
        entity.setEncryptedCode(encryptionService.encrypt(plainCode));
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return new CreatedRegistrationCode(repository.save(entity), plainCode);
    }

    public ReporterRegistrationCodeEntity update(String id, Map<String, Object> request) {
        ReporterRegistrationCodeEntity entity = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Registration code not found: " + id));
        if (request.containsKey("name")) {
            entity.setName(normalizeName(String.valueOf(request.getOrDefault("name", ""))));
        }
        if (request.containsKey("description")) {
            entity.setDescription(request.get("description") == null ? null : blankToNull(String.valueOf(request.get("description"))));
        }
        if (request.containsKey("enabled")) {
            entity.setEnabled(Boolean.parseBoolean(String.valueOf(request.get("enabled"))));
        }
        if (request.containsKey("code") && request.get("code") != null && !String.valueOf(request.get("code")).isBlank()) {
            String nextCode = String.valueOf(request.get("code")).trim();
            validateCode(nextCode);
            entity.setCodeHash(passwordEncoder.encode(nextCode));
            entity.setEncryptedCode(encryptionService.encrypt(nextCode));
        }
        entity.setUpdatedAt(Instant.now());
        return repository.save(entity);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean verify(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return repository.findByEnabledTrue().stream().anyMatch(entity -> passwordEncoder.matches(code, entity.getCodeHash()));
    }

    public String decryptCode(ReporterRegistrationCodeEntity entity) {
        if (entity.getEncryptedCode() == null || entity.getEncryptedCode().isBlank()) {
            return null;
        }
        return encryptionService.decrypt(entity.getEncryptedCode());
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Registration code name cannot be empty");
        }
        if (value.length() > 80) {
            throw new IllegalArgumentException("Registration code name is too long");
        }
        return value;
    }

    private void validateCode(String code) {
        if (code == null || code.length() < 8) {
            throw new IllegalArgumentException("Registration code must be at least 8 characters");
        }
        if (code.length() > 128) {
            throw new IllegalArgumentException("Registration code is too long");
        }
    }

    private String blankToNull(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    public record CreatedRegistrationCode(ReporterRegistrationCodeEntity entity, String code) {
    }
}
