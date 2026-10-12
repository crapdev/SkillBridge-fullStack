package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.*;
import com.riwi.skillbridge.infrastructure.security.AuthCookies;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthUseCase auth;
    private final AuthCookies cookies;

    public AuthController(AuthUseCase auth, AuthCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        String token = auth.register(request.name(), request.email(), request.password());
        return session(HttpStatus.CREATED, token, request.email());
    }

    // 202: la solicitud queda registrada pero la cuenta no se puede usar hasta que un admin la apruebe
    @PostMapping("/register/provider")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProviderRegistrationResponse registerProvider(@Valid @RequestBody RegisterRequest request) {
        auth.registerProvider(request.name(), request.email(), request.password());
        return new ProviderRegistrationResponse("PENDING_APPROVAL",
                "Tu solicitud fue enviada. Podrás iniciar sesión cuando un administrador apruebe tu cuenta.");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = auth.login(request.email(), request.password());
        return session(HttpStatus.OK, token, request.email());
    }

    // El frontend no puede leer la cookie HttpOnly: pregunta aquí quién es el usuario de la sesión
    @GetMapping("/me")
    public SessionUserResponse me(Authentication authentication) {
        return SessionUserResponse.from(auth.currentUser(authentication.getName()));
    }

    // Público a propósito: borrar la cookie no le da nada a nadie y permite salir aunque el token ya expiró
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.cleared().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> session(HttpStatus status, String token, String email) {
        SessionUserResponse user = SessionUserResponse.from(auth.currentUser(email));
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.session(token).toString())
                .body(new AuthResponse(token, "Bearer", user));
    }
}
