package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.AccountNotActiveException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositoryPort users;

    @Mock
    private PasswordHasherPort passwords;

    @Mock
    private TokenPort tokens;

    @InjectMocks
    private AuthService authService;

    // ==========================================
    // TESTS PARA EL MÉTODO REGISTER
    // ==========================================

    @Test
    void register_ShouldReturnToken_WhenSuccessful() {
        // Arrange
        String name = "Daniel";
        String email = " daniel@email.com "; // Con espacios para probar el .trim()
        String rawPassword = "password123";
        String normalizedEmail = "daniel@email.com";

        when(users.existsByEmail(normalizedEmail)).thenReturn(false);
        when(passwords.encode(rawPassword)).thenReturn("hashed_password");

        UserAccount mockSavedUser = new UserAccount(
            UUID.randomUUID(),
            "Daniel",
            normalizedEmail,
            "hashed_password",
            Role.CUSTOMER
        );

        when(users.save(any(UserAccount.class))).thenReturn(mockSavedUser);
        when(tokens.generate(normalizedEmail, Role.CUSTOMER.name())).thenReturn("mocked_jwt_token");

        // Act
        String token = authService.register(name, email, rawPassword);

        // Assert
        assertEquals("mocked_jwt_token", token);
        verify(users, times(1)).existsByEmail(normalizedEmail);
        verify(users, times(1)).save(any(UserAccount.class));
        verify(tokens, times(1)).generate(normalizedEmail, Role.CUSTOMER.name());
    }

    @Test
    void register_ShouldThrowException_WhenEmailExists() {
        // Arrange
        String email = "daniel@email.com";
        when(users.existsByEmail(email)).thenReturn(true);

        // Act & Assert
        BusinessRuleException exception = assertThrows(
            BusinessRuleException.class,
            () -> authService.register("Daniel", email, "password")
        );

        assertEquals("El correo ya está registrado", exception.getMessage());

        // Verificamos que al fallar la validación, nunca guarda ni genera tokens
        verify(users, never()).save(any());
        verify(passwords, never()).encode(anyString());
        verify(tokens, never()).generate(anyString(), anyString());
    }

    // ==========================================
    // TESTS PARA EL MÉTODO LOGIN
    // ==========================================

    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() {
        // Arrange
        String email = " daniel@email.com ";
        String rawPassword = "password123";
        String normalizedEmail = "daniel@email.com";

        UserAccount mockUser = new UserAccount(
            UUID.randomUUID(),
            "Daniel",
            normalizedEmail,
            "hashed_password",
            Role.CUSTOMER
        );

        when(users.findByEmail(normalizedEmail)).thenReturn(Optional.of(mockUser));
        when(passwords.matches(rawPassword, "hashed_password")).thenReturn(true);
        when(tokens.generate(normalizedEmail, Role.CUSTOMER.name())).thenReturn("mocked_jwt_token");

        // Act
        String token = authService.login(email, rawPassword);

        // Assert
        assertEquals("mocked_jwt_token", token);
        verify(users, times(1)).findByEmail(normalizedEmail);
        verify(passwords, times(1)).matches(rawPassword, "hashed_password");
        verify(tokens, times(1)).generate(normalizedEmail, Role.CUSTOMER.name());
    }

    @Test
    void login_ShouldThrowException_WhenEmailDoesNotExist() {
        // Arrange
        String email = "fantasma@email.com";
        when(users.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        InvalidCredentialsException exception = assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login(email, "password")
        );

        assertEquals("Email inexistente o contrasena erronea", exception.getMessage());

        // Verificamos que si no existe el email, no intenta validar hashes ni crear tokens
        verify(passwords, never()).matches(anyString(), anyString());
        verify(tokens, never()).generate(anyString(), anyString());
    }

    @Test
    void login_ShouldThrowException_WhenPasswordIsIncorrect() {
        // Arrange
        String email = "daniel@email.com";
        String rawPassword = "wrong_password";

        UserAccount mockUser = new UserAccount(
            UUID.randomUUID(),
            "Daniel",
            email,
            "hashed_password",
            Role.CUSTOMER
        );

        when(users.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(passwords.matches(rawPassword, "hashed_password")).thenReturn(false);

        // Act & Assert
        InvalidCredentialsException exception = assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login(email, rawPassword)
        );

        assertEquals("Email inexistente o contrasena erronea", exception.getMessage());

        // Verificamos que al fallar la contraseña, el token no se genera
        verify(tokens, never()).generate(anyString(), anyString());
    }

    // ==========================================
    // TESTS PARA PROVEEDORES PENDIENTES DE APROBACIÓN
    // ==========================================

    @Test
    void registerProvider_ShouldSavePendingProvider_AndNotIssueToken() {
        // Arrange
        when(users.existsByEmail("ana@email.com")).thenReturn(false);
        when(passwords.encode("password123")).thenReturn("hashed_password");
        when(users.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        authService.registerProvider(" Ana ", " Ana@Email.com ", "password123");

        // Assert: se guarda como PROVIDER pendiente y no se emite token
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertEquals(Role.PROVIDER, saved.getValue().role());
        assertEquals(AccountStatus.PENDING_APPROVAL, saved.getValue().status());
        assertEquals("ana@email.com", saved.getValue().email());
        verify(tokens, never()).generate(anyString(), anyString());
    }

    @Test
    void registerProvider_ShouldThrowException_WhenEmailAlreadyExists() {
        when(users.existsByEmail("ana@email.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class,
            () -> authService.registerProvider("Ana", "ana@email.com", "password123"));

        verify(users, never()).save(any(UserAccount.class));
    }

    @Test
    void login_ShouldThrowAccountNotActive_WhenProviderIsPending() {
        UserAccount pending = new UserAccount(UUID.randomUUID(), "Ana", "ana@email.com",
            "hashed_password", Role.PROVIDER, AccountStatus.PENDING_APPROVAL);
        when(users.findByEmail("ana@email.com")).thenReturn(Optional.of(pending));
        when(passwords.matches("password123", "hashed_password")).thenReturn(true);

        AccountNotActiveException exception = assertThrows(AccountNotActiveException.class,
            () -> authService.login("ana@email.com", "password123"));

        assertTrue(exception.getMessage().contains("pendiente de aprobación"));
        verify(tokens, never()).generate(anyString(), anyString());
    }

    @Test
    void login_ShouldThrowAccountNotActive_WhenProviderWasRejected() {
        UserAccount rejected = new UserAccount(UUID.randomUUID(), "Ana", "ana@email.com",
            "hashed_password", Role.PROVIDER, AccountStatus.REJECTED);
        when(users.findByEmail("ana@email.com")).thenReturn(Optional.of(rejected));
        when(passwords.matches("password123", "hashed_password")).thenReturn(true);

        assertThrows(AccountNotActiveException.class,
            () -> authService.login("ana@email.com", "password123"));

        verify(tokens, never()).generate(anyString(), anyString());
    }

    @Test
    void login_ShouldNotRevealPendingStatus_WhenPasswordIsIncorrect() {
        // Sin la contraseña correcta, una cuenta pendiente responde igual que cualquier credencial inválida
        UserAccount pending = new UserAccount(UUID.randomUUID(), "Ana", "ana@email.com",
            "hashed_password", Role.PROVIDER, AccountStatus.PENDING_APPROVAL);
        when(users.findByEmail("ana@email.com")).thenReturn(Optional.of(pending));
        when(passwords.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
            () -> authService.login("ana@email.com", "wrong_password"));
    }
}
