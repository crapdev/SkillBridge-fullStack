package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.AccountNotActiveException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

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
    public String login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase())
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
        if (user.status() == AccountStatus.INACTIVE) {
            throw new AccountNotActiveException("Tu cuenta fue desactivada por un administrador");
        }
        return tokens.generate(user.email(), user.role().name());
    }

    @Override
    public UserAccount currentUser(String email) {
        return users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
    }
}
