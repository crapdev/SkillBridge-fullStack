package com.riwi.skillbridge.application.port.in;

public interface AuthUseCase {
    String register(String name, String email, String rawPassword);

    /** Crea la cuenta de proveedor en estado pendiente; no devuelve token hasta que un admin la apruebe. */
    void registerProvider(String name, String email, String rawPassword);

    String login(String email, String rawPassword);
}
