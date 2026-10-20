package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.UserAccount;

public interface AuthUseCase {
    String register(String name, String email, String rawPassword);

    /** Crea la cuenta de proveedor en estado pendiente; no devuelve token hasta que un admin la apruebe. */
    void registerProvider(String name, String email, String rawPassword);

    String login(String email, String rawPassword);

    /** Usuario de la sesión actual: el frontend lo pide al arrancar porque ya no puede leer el JWT (HU-29). */
    UserAccount currentUser(String email);
}
