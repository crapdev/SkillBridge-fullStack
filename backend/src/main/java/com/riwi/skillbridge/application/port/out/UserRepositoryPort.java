package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

public interface UserRepositoryPort {
    boolean existsByEmail(String email);
    Optional<UserAccount> findByEmail(String email);
    UserAccount save(UserAccount user);
    
    long countByRole(Role role);
    long countByRoleAndCreatedAtGreaterThanEqual(Role role, Instant createdAt);
    Page<UserAccount> findUsers(Role role, String query, Pageable pageable);
    Optional<UserAccount> findById(java.util.UUID id);
}
