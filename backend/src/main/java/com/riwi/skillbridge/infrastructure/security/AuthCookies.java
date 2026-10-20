package com.riwi.skillbridge.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie de sesión con el JWT (HU-29). HttpOnly: el JavaScript de la página no puede leerla, así que un
 * XSS no puede robar la sesión. SameSite=Strict: el navegador no la envía en peticiones que vienen de
 * otros sitios. Path=/api: solo viaja a la API, no a cada recurso estático.
 */
@Component
public class AuthCookies {
    public static final String NAME = "sb_token";
    private static final String PATH = "/api";

    private final boolean secure;
    private final Duration maxAge;

    public AuthCookies(@Value("${app.auth.cookie-secure:true}") boolean secure,
                       @Value("${app.jwt.expiration}") long jwtExpirationMs) {
        this.secure = secure;
        // La cookie vive lo mismo que el JWT que lleva dentro
        this.maxAge = Duration.ofMillis(jwtExpirationMs);
    }

    public ResponseCookie session(String token) {
        return base(token).maxAge(maxAge).build();
    }

    /** Misma cookie vacía y con Max-Age=0: el navegador la borra. */
    public ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                // Solo por HTTPS; en local con http://localhost se desactiva con AUTH_COOKIE_SECURE=false
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
