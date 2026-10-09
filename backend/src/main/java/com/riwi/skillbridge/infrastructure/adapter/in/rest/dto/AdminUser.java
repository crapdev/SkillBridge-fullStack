package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import java.time.Instant;
import java.util.UUID;

/** `active` es un atajo de `status == ACTIVE`: indica si la cuenta puede iniciar sesión. */
public record AdminUser(
    UUID id,
    String name,
    String email,
    Role role,
    AccountStatus status,
    boolean active,
    Instant createdAt
) {
    public static AdminUser fromDomain(UserAccount user) {
        return new AdminUser(user.id(), user.name(), user.email(), user.role(), user.status(),
                user.status() == AccountStatus.ACTIVE, user.createdAt());
    }
}
