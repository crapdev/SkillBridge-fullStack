package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record AdminUser(
    UUID id,
    String name,
    String email,
    Role role,
    Instant createdAt
) {
    public static AdminUser fromDomain(UserAccount user) {
        return new AdminUser(user.id(), user.name(), user.email(), user.role(), user.createdAt());
    }
}
