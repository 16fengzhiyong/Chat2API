package com.chat2api.backend.repository;

import com.chat2api.backend.domain.RequestLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestLogRepository extends JpaRepository<RequestLogEntity, String> {
}
