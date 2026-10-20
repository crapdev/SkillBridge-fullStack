package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.UserAccount;

/** Datos de la sesión que necesita el frontend (header, guards por rol). Nunca incluye el hash de la contraseña. */
public record SessionUserResponse(String email, String name, String role) {
    public static SessionUserResponse from(UserAccount user) {
        return new SessionUserResponse(user.email(), user.name(), user.role().name());
    }
}
