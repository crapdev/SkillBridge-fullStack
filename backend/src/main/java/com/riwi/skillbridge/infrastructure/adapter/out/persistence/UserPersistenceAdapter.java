package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
                user.id(), user.name(), user.email(), user.passwordHash(), user.role(), createdAt));
        return toDomain(saved);
    }

    @Override
    public Optional<UUID> findIdByEmail(String email) { return findByEmail(email).map(UserAccount::id); }

    @Override
    public long countByRole(com.riwi.skillbridge.domain.model.Role role) {
        return repository.countByRole(role);
    }

    @Override
    public long countByRoleAndCreatedAtGreaterThanEqual(com.riwi.skillbridge.domain.model.Role role, Instant createdAt) {
        return repository.countByRoleAndCreatedAtGreaterThanEqual(role, createdAt);
    }

    @Override
    public org.springframework.data.domain.Page<UserAccount> findUsers(com.riwi.skillbridge.domain.model.Role role, String query, org.springframework.data.domain.Pageable pageable) {
        return repository.findUsers(role, query, pageable).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    private UserAccount toDomain(UserEntity e) {
        return new UserAccount(e.getId(), e.getName(), e.getEmail(), e.getPassword(), e.getRole(), e.getCreatedAt());
    }
}
