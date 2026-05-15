package com.chat2api.backend.repository;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    List<AccountEntity> findByProviderId(String providerId);
    List<AccountEntity> findByProviderIdAndStatus(String providerId, AccountStatus status);
    long countByProviderId(String providerId);
    @Query("SELECT a.providerId, COUNT(a) FROM AccountEntity a GROUP BY a.providerId")
    List<Object[]> countGroupedByProviderId();
    Page<AccountEntity> findByProviderId(String providerId, Pageable pageable);
    @Query("SELECT a FROM AccountEntity a WHERE a.providerId = :providerId AND (LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(COALESCE(a.email, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AccountEntity> searchByProviderId(@Param("providerId") String providerId, @Param("search") String search, Pageable pageable);
}
