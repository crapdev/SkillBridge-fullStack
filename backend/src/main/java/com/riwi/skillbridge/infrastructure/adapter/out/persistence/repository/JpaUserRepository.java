package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);
    long countByRoleAndCreatedAtGreaterThanEqual(Role role, Instant createdAt);
    long countByRoleAndStatusIn(Role role, Collection<AccountStatus> statuses);

    @Query("SELECT u FROM UserEntity u WHERE u.role = :role AND u.status IN :statuses"
            + " AND (:query = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))"
            + " OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<UserEntity> findUsers(@Param("role") Role role, @Param("query") String query,
                               @Param("statuses") Collection<AccountStatus> statuses, Pageable pageable);
}
