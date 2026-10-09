package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthUseCase auth;
    public AuthController(AuthUseCase auth) { this.auth = auth; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return new AuthResponse(auth.register(request.name(), request.email(), request.password()), "Bearer");
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
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return new AuthResponse(auth.login(request.email(), request.password()), "Bearer");
    }
}
