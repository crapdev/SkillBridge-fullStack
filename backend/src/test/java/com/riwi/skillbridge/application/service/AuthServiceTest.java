package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.AccountNotActiveException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Authentication Service Tests")
class AuthServiceTest {

    private final UserRepositoryPort userRepository = mock(UserRepositoryPort.class);
    private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);
    private final TokenPort tokenPort = mock(TokenPort.class);
    private final AuthService authService = new AuthService(userRepository, passwordHasher, tokenPort);

    @Nested
    @DisplayName("User Login")
    class LoginTests {

        @Test
        @DisplayName("Should successfully login with correct credentials")
        void shouldSuccessfullyLoginWithCorrectCredentials() {
            // Arrange
            String email = "user@example.com";
            String password = "CorrectPassword123!";
            String passwordHash = "hashed_correct_password";
            UUID userId = UUID.randomUUID();
            UserAccount user = new UserAccount(userId, "John", email, passwordHash, Role.CUSTOMER);
            String expectedToken = "jwt_login_token";

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(passwordHasher.matches(password, passwordHash)).thenReturn(true);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn(expectedToken);

            // Act
            String token = authService.login(email, password);

            // Assert
            assertEquals(expectedToken, token);
            verify(userRepository).findByEmail("user@example.com");
            verify(passwordHasher).matches(password, passwordHash);
            verify(tokenPort).generate(email, "CUSTOMER");
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException when email not found")
        void shouldThrowExceptionWhenEmailNotFound() {
            // Arrange
            String email = "nonexistent@example.com";

            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            // Act & Assert
            InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () ->
                authService.login(email, "password")
            );
            assertEquals("Email inexistente o contrasena erronea", exception.getMessage());
            verify(passwordHasher, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException when password is incorrect")
        void shouldThrowExceptionWhenPasswordIncorrect() {
            // Arrange
            String email = "user@example.com";
            String wrongPassword = "WrongPassword";
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", email, "correct_hash", Role.CUSTOMER);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(wrongPassword, "correct_hash")).thenReturn(false);

            // Act & Assert
            InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class, () ->
                authService.login(email, wrongPassword)
            );
            assertEquals("Email inexistente o contrasena erronea", exception.getMessage());
            verify(tokenPort, never()).generate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should normalize email to lowercase during login")
        void shouldNormalizeEmailToLowercaseDuringLogin() {
            // Arrange
            String email = "USER@EXAMPLE.COM";
            String normalizedEmail = "user@example.com";
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", normalizedEmail, "hash", Role.CUSTOMER);

            when(userRepository.findByEmail(normalizedEmail)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(anyString(), anyString())).thenReturn(true);
            when(tokenPort.generate(normalizedEmail, "CUSTOMER")).thenReturn("token");

            // Act
            authService.login(email, "password");

            // Assert
            verify(userRepository).findByEmail(normalizedEmail);
        }

        @Test
        @DisplayName("Should trim whitespace from email during login")
        void shouldTrimWhitespaceFromEmailDuringLogin() {
            // Arrange
            String email = "  user@example.com  ";
            String normalizedEmail = "user@example.com";
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", normalizedEmail, "hash", Role.CUSTOMER);

            when(userRepository.findByEmail(normalizedEmail)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(anyString(), anyString())).thenReturn(true);
            when(tokenPort.generate(normalizedEmail, "CUSTOMER")).thenReturn("token");

            // Act
            authService.login(email, "password");

            // Assert
            verify(userRepository).findByEmail(normalizedEmail);
        }

        @Test
        @DisplayName("Should generate token with user email and role")
        void shouldGenerateTokenWithUserEmailAndRole() {
            // Arrange
            String email = "user@example.com";
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", email, "hash", Role.CUSTOMER);
            String expectedToken = "jwt_token_abc";

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(anyString(), anyString())).thenReturn(true);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn(expectedToken);

            // Act
            String token = authService.login(email, "password");

            // Assert
            assertEquals(expectedToken, token);
            verify(tokenPort).generate(email, "CUSTOMER");
        }

        @Test
        @DisplayName("Should not generate token on failed login attempt")
        void shouldNotGenerateTokenOnFailedLogin() {
            // Arrange
            String email = "user@example.com";
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", email, "hash", Role.CUSTOMER);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, () ->
                authService.login(email, "wrongPassword")
            );
            verify(tokenPort, never()).generate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle email with international characters")
        void shouldHandleEmailWithInternationalCharacters() {
            // Arrange
            String email = "usér@example.com";
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", email, "hash", Role.CUSTOMER);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(anyString(), anyString())).thenReturn(true);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn("token");

            // Act
            authService.login(email, "password");

            // Assert
            verify(userRepository).findByEmail(email);
        }

        @Test
        @DisplayName("Should handle very long password input")
        void shouldHandleVeryLongPasswordInput() {
            // Arrange
            String email = "user@example.com";
            String longPassword = "A".repeat(500);
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", email, "hash", Role.CUSTOMER);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(longPassword, "hash")).thenReturn(true);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn("token");

            // Act
            String token = authService.login(email, longPassword);

            // Assert
            assertNotNull(token);
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle case sensitivity correctly in password check")
        void shouldHandleCaseSensitivityInPasswordCheck() {
            // Arrange
            String email = "user@example.com";
            UserAccount user = new UserAccount(UUID.randomUUID(), "John", email, "hash", Role.CUSTOMER);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches("Password", "hash")).thenReturn(false);

            // Act & Assert
            assertThrows(InvalidCredentialsException.class, () ->
                authService.login(email, "Password")
            );
        }
    }

    @Nested
    @DisplayName("Provider Account Status and Login")
    class ProviderAccountStatusTests {

        @Test
        @DisplayName("Should block login while the provider is pending approval")
        void login_ShouldThrowAccountNotActive_WhenProviderIsPending() {
            UserAccount pending = new UserAccount(UUID.randomUUID(), "Ana", "ana@email.com",
                "hashed_password", Role.PROVIDER, AccountStatus.PENDING_APPROVAL);
            when(userRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(pending));
            when(passwordHasher.matches("password123", "hashed_password")).thenReturn(true);

            AccountNotActiveException exception = assertThrows(AccountNotActiveException.class,
                () -> authService.login("ana@email.com", "password123"));

            assertTrue(exception.getMessage().contains("pendiente de aprobación"));
            verify(tokenPort, never()).generate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should block login when the provider was rejected")
        void login_ShouldThrowAccountNotActive_WhenProviderWasRejected() {
            UserAccount rejected = new UserAccount(UUID.randomUUID(), "Ana", "ana@email.com",
                "hashed_password", Role.PROVIDER, AccountStatus.REJECTED);
            when(userRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(rejected));
            when(passwordHasher.matches("password123", "hashed_password")).thenReturn(true);

            assertThrows(AccountNotActiveException.class,
                () -> authService.login("ana@email.com", "password123"));

            verify(tokenPort, never()).generate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should not reveal pending status when the password is wrong")
        void login_ShouldNotRevealPendingStatus_WhenPasswordIsIncorrect() {
            UserAccount pending = new UserAccount(UUID.randomUUID(), "Ana", "ana@email.com",
                "hashed_password", Role.PROVIDER, AccountStatus.PENDING_APPROVAL);
            when(userRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(pending));
            when(passwordHasher.matches("wrong_password", "hashed_password")).thenReturn(false);

            assertThrows(InvalidCredentialsException.class,
                () -> authService.login("ana@email.com", "wrong_password"));
        }
    }

    @Nested
    @DisplayName("Current User (HU-29)")
    class CurrentUserTests {

        @Test
        @DisplayName("Should return the user of the session, normalizing the email")
        void currentUser_ShouldReturnTheAccount() {
            UserAccount account = new UserAccount(UUID.randomUUID(), "Sofía", "sofia@email.com", "hash", Role.CUSTOMER);
            when(userRepository.findByEmail("sofia@email.com")).thenReturn(Optional.of(account));

            assertEquals(account, authService.currentUser(" Sofia@Email.com "));
        }

        @Test
        @DisplayName("Should fail when the user no longer exists")
        void currentUser_ShouldThrow_WhenUserDoesNotExist() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class, () -> authService.currentUser("nadie@email.com"));
        }
    }
}
