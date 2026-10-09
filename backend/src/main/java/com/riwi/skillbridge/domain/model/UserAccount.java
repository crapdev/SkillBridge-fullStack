package com.riwi.skillbridge.domain.model;

import java.util.UUID;

public record UserAccount(UUID id, String name, String email, String passwordHash, Role role, AccountStatus status) {

    /** Cuenta activa: el caso habitual para clientes y administradores. */
    public UserAccount(UUID id, String name, String email, String passwordHash, Role role) {
        this(id, name, email, passwordHash, role, AccountStatus.ACTIVE);
    }
}
