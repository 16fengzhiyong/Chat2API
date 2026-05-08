package com.chat2api.backend.repository;

import com.chat2api.backend.domain.AppConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfigEntity, String> {
}
