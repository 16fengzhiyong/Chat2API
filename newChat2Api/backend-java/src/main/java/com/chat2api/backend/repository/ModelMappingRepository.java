package com.chat2api.backend.repository;

import com.chat2api.backend.domain.ModelMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelMappingRepository extends JpaRepository<ModelMappingEntity, String> {
}
