package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort, UserAccountPort {
    private final JpaUserRepository repository;

    public UserPersistenceAdapter(JpaUserRepository repository) { this.repository = repository; }

    @Override
    public boolean existsByEmail(String email) { return repository.existsByEmailIgnoreCase(email); }

    @Override
    public Optional<UserAccount> findByEmail(String email) { return repository.findByEmailIgnoreCase(email).map(this::toDomain); }

    @Override
    public UserAccount save(UserAccount user) {
        Instant createdAt = user.createdAt() != null ? user.createdAt() : Instant.now();
        UserEntity saved = repository.save(new UserEntity(
                user.id(), user.name(), user.email(), user.passwordHash(), user.role(), user.status(), createdAt));
        return toDomain(saved);
    }

    @Override
    public Optional<UUID> findIdByEmail(String email) { return findByEmail(email).map(UserAccount::id); }

    @Override
    public long countByRole(Role role) {
        return repository.countByRole(role);
    }

    @Override
    public long countByRoleAndCreatedAtGreaterThanEqual(Role role, Instant createdAt) {
        return repository.countByRoleAndCreatedAtGreaterThanEqual(role, createdAt);
    }

    @Override
    public long countByRoleAndStatusIn(Role role, Collection<AccountStatus> statuses) {
        return repository.countByRoleAndStatusIn(role, statuses);
    }

    @Override
    public Page<UserAccount> findUsers(Role role, String query, Collection<AccountStatus> statuses, Pageable pageable) {
        // Un IN con lista vacía no es válido en JPQL: "sin filtro" se traduce a todos los estados
        Collection<AccountStatus> filter = statuses == null || statuses.isEmpty() ? EnumSet.allOf(AccountStatus.class) : statuses;
        return repository.findUsers(role, query, filter, pageable).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    private UserAccount toDomain(UserEntity e) {
        return new UserAccount(e.getId(), e.getName(), e.getEmail(), e.getPassword(), e.getRole(), e.getStatus(), e.getCreatedAt());
    }
}
