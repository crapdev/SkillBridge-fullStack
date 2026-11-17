package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.UserAccount;

public interface AuthUseCase {
    String login(String email, String rawPassword);

    /** Usuario de la sesión actual: el frontend lo pide al arrancar porque ya no puede leer el JWT (HU-29). */
    UserAccount currentUser(String email);
}
