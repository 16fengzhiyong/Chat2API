package com.chat2api.backend.repository;

import com.chat2api.backend.domain.SystemPromptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemPromptRepository extends JpaRepository<SystemPromptEntity, String> {
    List<SystemPromptEntity> findByCategory(String category);
}
