package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.AccountNotActiveException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService implements AuthUseCase {
    private final UserRepositoryPort users;
    private final PasswordHasherPort passwords;
    private final TokenPort tokens;

    public AuthService(UserRepositoryPort users, PasswordHasherPort passwords, TokenPort tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Override
    public String register(String name, String email, String rawPassword) {
        UserAccount saved = createAccount(name, email, rawPassword, Role.CUSTOMER, AccountStatus.ACTIVE);
        return tokens.generate(saved.email(), saved.role().name());
    }

    @Override
    public void registerProvider(String name, String email, String rawPassword) {
        createAccount(name, email, rawPassword, Role.PROVIDER, AccountStatus.PENDING_APPROVAL);
    }

    private UserAccount createAccount(String name, String email, String rawPassword, Role role, AccountStatus status) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException("El correo ya está registrado");
        }
        return users.save(new UserAccount(
                UUID.randomUUID(), name.trim(), normalizedEmail, passwords.encode(rawPassword), role, status));
    }

    @Override
    public String login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase())
            // Cambio aquí: unificar la excepción lanzada. InvalidCredentialsException en lugar de BusinessRuleException
                .orElseThrow(() -> new InvalidCredentialsException("Email inexistente o contrasena erronea"));
        if (!passwords.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException("Email inexistente o contrasena erronea");
        }
        // El estado se revisa después de validar la contraseña para no revelar a terceros qué cuentas existen
        if (user.status() == AccountStatus.PENDING_APPROVAL) {
            throw new AccountNotActiveException("Tu cuenta de proveedor está pendiente de aprobación por un administrador");
        }
        if (user.status() == AccountStatus.REJECTED) {
            throw new AccountNotActiveException("Tu solicitud de cuenta de proveedor fue rechazada");
        }
        return tokens.generate(user.email(), user.role().name());
    }
}
