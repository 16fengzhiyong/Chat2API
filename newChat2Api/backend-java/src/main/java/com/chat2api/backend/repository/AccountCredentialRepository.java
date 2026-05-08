package com.chat2api.backend.repository;

import com.chat2api.backend.domain.AccountCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountCredentialRepository extends JpaRepository<AccountCredentialEntity, String> {
}
