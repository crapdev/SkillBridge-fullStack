package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    boolean existsByEmail(String email);
    Optional<UserAccount> findByEmail(String email);
    UserAccount save(UserAccount user);

    long countByRole(Role role);
    long countByRoleAndCreatedAtGreaterThanEqual(Role role, Instant createdAt);
    long countByRoleAndStatusIn(Role role, Collection<AccountStatus> statuses);
    /** `statuses` vacío o nulo significa "cualquier estado". */
    Page<UserAccount> findUsers(Role role, String query, Collection<AccountStatus> statuses, Pageable pageable);
    Optional<UserAccount> findById(UUID id);
}
