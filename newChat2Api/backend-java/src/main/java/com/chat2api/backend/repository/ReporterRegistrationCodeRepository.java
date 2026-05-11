package com.chat2api.backend.repository;

import com.chat2api.backend.domain.ReporterRegistrationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReporterRegistrationCodeRepository extends JpaRepository<ReporterRegistrationCodeEntity, String> {
    List<ReporterRegistrationCodeEntity> findByEnabledTrue();
}
