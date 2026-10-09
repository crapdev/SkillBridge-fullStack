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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Authentication Service Tests")
class AuthServiceTest {

    private final UserRepositoryPort userRepository = mock(UserRepositoryPort.class);
    private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);
    private final TokenPort tokenPort = mock(TokenPort.class);
    private final AuthService authService = new AuthService(userRepository, passwordHasher, tokenPort);

    @Nested
    @DisplayName("User Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Should successfully register new user with valid credentials")
        void shouldRegisterNewUserWithValidCredentials() {
            // Arrange
            String name = "John Doe";
            String email = "john@example.com";
            String password = "SecurePassword123!";
            String encodedPassword = "hashed_password_value";
            String expectedToken = "jwt_token_xyz";

            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(passwordHasher.encode(password)).thenReturn(encodedPassword);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn(expectedToken);
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            String token = authService.register(name, email, password);

            // Assert
            assertEquals(expectedToken, token);
            verify(userRepository).existsByEmail("john@example.com");
            verify(passwordHasher).encode(password);
            verify(userRepository).save(any(UserAccount.class));
            verify(tokenPort).generate(email, "CUSTOMER");
        }

        @Test
        @DisplayName("Should normalize email to lowercase during registration")
        void shouldNormalizeEmailToLowercase() {
            // Arrange
            String email = "JOHN@EXAMPLE.COM";
            String normalizedEmail = "john@example.com";

            when(userRepository.existsByEmail(normalizedEmail)).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(normalizedEmail, "CUSTOMER")).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            authService.register("John", email, "password");

            // Assert
            verify(userRepository).existsByEmail(normalizedEmail);
            verify(tokenPort).generate(normalizedEmail, "CUSTOMER");
        }

        @Test
        @DisplayName("Should trim whitespace from email and name during registration")
        void shouldTrimWhitespaceFromEmailAndName() {
            // Arrange
            String name = "  John Doe  ";
            String email = "  john@example.com  ";
            String normalizedEmail = "john@example.com";

            when(userRepository.existsByEmail(normalizedEmail)).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(normalizedEmail, "CUSTOMER")).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount user = invocation.getArgument(0);
                assertEquals("John Doe", user.name());
                return user;
            });

            // Act
            authService.register(name, email, "password");

            // Assert
            verify(userRepository).save(any(UserAccount.class));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when email already exists")
        void shouldThrowExceptionWhenEmailAlreadyExists() {
            // Arrange
            String email = "existing@example.com";

            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            // Act & Assert
            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                authService.register("John", email, "password")
            );
            assertEquals("El correo ya está registrado", exception.getMessage());
            verify(userRepository, never()).save(any());
            verify(passwordHasher, never()).encode(anyString());
        }

        @Test
        @DisplayName("Should create user with CUSTOMER role")
        void shouldCreateUserWithCustomerRole() {
            // Arrange
            String email = "customer@example.com";

            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(anyString(), anyString())).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount user = invocation.getArgument(0);
                assertEquals(Role.CUSTOMER, user.role());
                return user;
            });

            // Act
            authService.register("User", email, "password");

            // Assert
            verify(userRepository).save(any(UserAccount.class));
        }

        @Test
        @DisplayName("Should encode password during registration")
        void shouldEncodePasswordDuringRegistration() {
            // Arrange
            String rawPassword = "MyPassword123!";
            String encodedPassword = "$2b$12$hashed_value";

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordHasher.encode(rawPassword)).thenReturn(encodedPassword);
            when(tokenPort.generate(anyString(), anyString())).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount user = invocation.getArgument(0);
                assertEquals(encodedPassword, user.passwordHash());
                return user;
            });

            // Act
            authService.register("User", "user@example.com", rawPassword);

            // Assert
            verify(passwordHasher).encode(rawPassword);
        }

        @Test
        @DisplayName("Should generate JWT token with user email and CUSTOMER role")
        void shouldGenerateJwtTokenWithEmailAndRole() {
            // Arrange
            String email = "user@example.com";
            String expectedToken = "jwt_token_123";

            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn(expectedToken);
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            String token = authService.register("User", email, "password");

            // Assert
            assertEquals(expectedToken, token);
            verify(tokenPort).generate(email, "CUSTOMER");
        }

        @Test
        @DisplayName("Should handle email with special characters")
        void shouldHandleEmailWithSpecialCharacters() {
            // Arrange
            String email = "user+tag@example.co.uk";
            String normalizedEmail = "user+tag@example.co.uk";

            when(userRepository.existsByEmail(normalizedEmail)).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(normalizedEmail, "CUSTOMER")).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            authService.register("User", email, "password");

            // Assert
            verify(userRepository).existsByEmail(normalizedEmail);
        }

        @Test
        @DisplayName("Should handle very long user name")
        void shouldHandleVeryLongUserName() {
            // Arrange
            String longName = "A".repeat(100);
            String email = "user@example.com";

            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(anyString(), anyString())).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount user = invocation.getArgument(0);
                assertEquals(longName, user.name());
                return user;
            });

            // Act
            authService.register(longName, email, "password");

            // Assert
            verify(userRepository).save(any(UserAccount.class));
        }
    }

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
        @DisplayName("Should propagate repository exceptions during registration")
        void shouldPropagateRepositoryExceptionsDuringRegistration() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenThrow(new RuntimeException("Database error"));

            // Act & Assert
            assertThrows(RuntimeException.class, () ->
                authService.register("User", "user@example.com", "password")
            );
        }

        @Test
        @DisplayName("Should propagate password encoder exceptions")
        void shouldPropagatePasswordEncoderExceptions() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenThrow(new RuntimeException("Encoding error"));

            // Act & Assert
            assertThrows(RuntimeException.class, () ->
                authService.register("User", "user@example.com", "password")
            );
        }

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

        @Test
        @DisplayName("Should handle registration with empty name after trim")
        void shouldHandleRegistrationWithEmptyNameAfterTrim() {
            // Arrange
            String emptyName = "   ";
            String email = "user@example.com";

            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(anyString(), anyString())).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
                UserAccount user = invocation.getArgument(0);
                assertEquals("", user.name());
                return user;
            });

            // Act
            authService.register(emptyName, email, "password");

            // Assert
            verify(userRepository).save(any(UserAccount.class));
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenariosTests {

        @Test
        @DisplayName("Should handle complete registration and login flow")
        void shouldHandleCompleteRegistrationAndLoginFlow() {
            // Arrange - Registration
            String name = "John Doe";
            String email = "john@example.com";
            String password = "SecurePassword123!";
            String encodedPassword = "hashed_password";
            String regToken = "reg_token_123";

            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordHasher.encode(password)).thenReturn(encodedPassword);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn(regToken);
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act - Register
            String registrationToken = authService.register(name, email, password);

            // Assert - Registration
            assertEquals(regToken, registrationToken);

            // Arrange - Login
            reset(userRepository, tokenPort);
            UserAccount user = new UserAccount(UUID.randomUUID(), name, email, encodedPassword, Role.CUSTOMER);
            String loginToken = "login_token_456";

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(password, encodedPassword)).thenReturn(true);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn(loginToken);

            // Act - Login
            String token = authService.login(email, password);

            // Assert - Login
            assertEquals(loginToken, token);
            verify(userRepository).findByEmail(email);
        }

        @Test
        @DisplayName("Should handle multiple user registrations")
        void shouldHandleMultipleUserRegistrations() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordHasher.encode(anyString())).thenReturn("hashed");
            when(tokenPort.generate(anyString(), anyString())).thenReturn("token");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            authService.register("User1", "user1@example.com", "password1");
            authService.register("User2", "user2@example.com", "password2");
            authService.register("User3", "user3@example.com", "password3");

            // Assert
            verify(userRepository, times(3)).save(any(UserAccount.class));
            verify(tokenPort, times(3)).generate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle login attempt after failed registration attempt")
        void shouldHandleLoginAfterFailedRegistration() {
            // Arrange - Failed registration (email exists)
            String email = "user@example.com";
            when(userRepository.existsByEmail(email)).thenReturn(true);

            // Act & Assert - Registration should fail
            assertThrows(BusinessRuleException.class, () ->
                authService.register("User", email, "password")
            );

            // Arrange - Login with existing user
            reset(userRepository);
            UserAccount user = new UserAccount(UUID.randomUUID(), "User", email, "hash", Role.CUSTOMER);
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordHasher.matches("password", "hash")).thenReturn(true);
            when(tokenPort.generate(email, "CUSTOMER")).thenReturn("token");

            // Act & Assert - Login should succeed
            String token = authService.login(email, "password");
            assertNotNull(token);
        }
    }

    @Nested
    @DisplayName("Provider Registration And Approval")
    class ProviderRegistrationTests {

        @Test
        @DisplayName("Should save provider as pending approval and not issue a token")
        void registerProvider_ShouldSavePendingProvider_AndNotIssueToken() {
            when(userRepository.existsByEmail("ana@email.com")).thenReturn(false);
            when(passwordHasher.encode("password123")).thenReturn("hashed_password");
            when(userRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

            authService.registerProvider(" Ana ", " Ana@Email.com ", "password123");

            ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
            verify(userRepository).save(saved.capture());
            assertEquals(Role.PROVIDER, saved.getValue().role());
            assertEquals(AccountStatus.PENDING_APPROVAL, saved.getValue().status());
            assertEquals("ana@email.com", saved.getValue().email());
            verify(tokenPort, never()).generate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should reject provider registration when email already exists")
        void registerProvider_ShouldThrowException_WhenEmailAlreadyExists() {
            when(userRepository.existsByEmail("ana@email.com")).thenReturn(true);

            assertThrows(BusinessRuleException.class,
                () -> authService.registerProvider("Ana", "ana@email.com", "password123"));

            verify(userRepository, never()).save(any(UserAccount.class));
        }

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
}
