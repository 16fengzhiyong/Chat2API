package com.chat2api.backend.repository;

import com.chat2api.backend.domain.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    List<SessionEntity> findByProviderId(String providerId);
    List<SessionEntity> findByAccountId(String accountId);
}
