package com.chat2api.backend.repository;

import com.chat2api.backend.domain.AdminUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUserEntity, String> {
    Optional<AdminUserEntity> findByUsernameAndEnabledTrue(String username);
}
