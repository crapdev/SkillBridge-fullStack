package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(UUID id, String name, String email, String passwordHash, Role role,
                          AccountStatus status, Instant createdAt) {

    /** Cuenta activa: el caso habitual para clientes y administradores. */
    public UserAccount(UUID id, String name, String email, String passwordHash, Role role) {
        this(id, name, email, passwordHash, role, AccountStatus.ACTIVE, null);
    }

    /** Cuenta nueva con un estado concreto; la fecha de alta la asigna la persistencia. */
    public UserAccount(UUID id, String name, String email, String passwordHash, Role role, AccountStatus status) {
        this(id, name, email, passwordHash, role, status, null);
    }
}
