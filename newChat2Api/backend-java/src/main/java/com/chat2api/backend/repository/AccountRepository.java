package com.chat2api.backend.repository;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    List<AccountEntity> findByProviderId(String providerId);
    List<AccountEntity> findByProviderIdAndStatus(String providerId, AccountStatus status);
}
