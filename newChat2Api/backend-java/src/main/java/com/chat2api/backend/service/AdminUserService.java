package com.chat2api.backend.service;

import com.chat2api.backend.domain.AdminUserEntity;
import com.chat2api.backend.repository.AdminUserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminUserService {
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultUsername;
    private final String defaultPassword;

    public AdminUserService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder, @Value("${chat2api.admin.username:admin}") String defaultUsername, @Value("${chat2api.admin.default-password:admin123456}") String defaultPassword) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @PostConstruct
    public void seedDefaultAdmin() {
        if (adminUserRepository.count() > 0) {
            return;
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername(defaultUsername == null || defaultUsername.isBlank() ? "admin" : defaultUsername.trim());
        admin.setPasswordHash(passwordEncoder.encode(defaultPassword == null || defaultPassword.isBlank() ? "admin123456" : defaultPassword));
        admin.setEnabled(true);
        admin.setMustChangePassword(true);
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        adminUserRepository.save(admin);
    }

    public AdminUserEntity requireEnabled(String username) {
        return adminUserRepository.findByUsernameAndEnabledTrue(username).orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
    }

    public boolean passwordMatches(AdminUserEntity user, String password) {
        return passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash());
    }

    @Transactional
    public AdminUserEntity changePassword(String username, String currentPassword, String newPassword) {
        AdminUserEntity user = requireEnabled(username);
        if (!passwordMatches(user, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        validateNewPassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setUpdatedAt(Instant.now());
        return adminUserRepository.save(user);
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        if (newPassword.length() > 128) {
            throw new IllegalArgumentException("New password is too long");
        }
        if ("admin123456".equals(newPassword)) {
            throw new IllegalArgumentException("New password cannot be the default password");
        }
    }
}
