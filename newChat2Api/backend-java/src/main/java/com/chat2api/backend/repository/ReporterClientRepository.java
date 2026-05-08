package com.chat2api.backend.repository;

import com.chat2api.backend.domain.ReporterClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReporterClientRepository extends JpaRepository<ReporterClientEntity, String> {
    Optional<ReporterClientEntity> findByIdAndSecret(String id, String secret);
}
