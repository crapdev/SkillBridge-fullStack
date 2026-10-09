package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    
    long countByRole(com.riwi.skillbridge.domain.model.Role role);
    long countByRoleAndCreatedAtGreaterThanEqual(com.riwi.skillbridge.domain.model.Role role, java.time.Instant createdAt);
    
    @org.springframework.data.jpa.repository.Query("SELECT u FROM UserEntity u WHERE u.role = :role AND (:query = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    org.springframework.data.domain.Page<UserEntity> findUsers(@org.springframework.data.repository.query.Param("role") com.riwi.skillbridge.domain.model.Role role, @org.springframework.data.repository.query.Param("query") String query, org.springframework.data.domain.Pageable pageable);
}
