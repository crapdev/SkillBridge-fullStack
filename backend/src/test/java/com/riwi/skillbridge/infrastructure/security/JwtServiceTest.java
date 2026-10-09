package com.riwi.skillbridge.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private static final String SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION_TIME);
    }

    @Nested
    @DisplayName("Token Generation")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate valid JWT token with username and role")
        void shouldGenerateValidToken() {
            // Arrange
            String username = "user@example.com";
            String role = "CUSTOMER";

            // Act
            String token = jwtService.generate(username, role);

            // Assert
            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertTrue(token.contains("."));
        }

        @Test
        @DisplayName("Should embed username in token")
        void shouldEmbedUsernameInToken() {
            // Arrange
            String username = "john@example.com";
            String role = "CUSTOMER";

            // Act
            String token = jwtService.generate(username, role);
            String extractedUsername = jwtService.extractUsername(token);

            // Assert
            assertEquals(username, extractedUsername);
        }

        @Test
        @DisplayName("Should generate different tokens for different usernames")
        void shouldGenerateDifferentTokensForDifferentUsernames() {
            // Arrange
            String username1 = "user1@example.com";
            String username2 = "user2@example.com";
            String role = "CUSTOMER";

            // Act
            String token1 = jwtService.generate(username1, role);
            String token2 = jwtService.generate(username2, role);

            // Assert
            assertNotEquals(token1, token2);
            assertEquals(username1, jwtService.extractUsername(token1));
            assertEquals(username2, jwtService.extractUsername(token2));
        }

        @Test
        @DisplayName("Should generate tokens with admin role")
        void shouldGenerateTokenWithAdminRole() {
            // Arrange
            String username = "admin@example.com";
            String role = "ADMIN";

            // Act
            String token = jwtService.generate(username, role);

            // Assert
            assertNotNull(token);
            assertEquals(username, jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Should generate token with special characters in username")
        void shouldGenerateTokenWithSpecialCharactersInUsername() {
            // Arrange
            String username = "user+tag@example.co.uk";
            String role = "CUSTOMER";

            // Act
            String token = jwtService.generate(username, role);

            // Assert
            assertEquals(username, jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Should generate token with numeric role")
        void shouldGenerateTokenWithNumericRole() {
            // Arrange
            String username = "user@example.com";
            String role = "ROLE_123";

            // Act
            String token = jwtService.generate(username, role);

            // Assert
            assertNotNull(token);
            assertFalse(token.isEmpty());
        }
    }

    @Nested
    @DisplayName("Username Extraction")
    class UsernameExtractionTests {

        @Test
        @DisplayName("Should extract username from valid token")
        void shouldExtractUsernameFromValidToken() {
            // Arrange
            String username = "testuser@example.com";
            String token = jwtService.generate(username, "CUSTOMER");

            // Act
            String extracted = jwtService.extractUsername(token);

            // Assert
            assertEquals(username, extracted);
        }

        @Test
        @DisplayName("Should throw exception when extracting from invalid token")
        void shouldThrowExceptionForInvalidToken() {
            // Arrange
            String invalidToken = "invalid.token.here";

            // Act & Assert
            assertThrows(Exception.class, () -> jwtService.extractUsername(invalidToken));
        }

        @Test
        @DisplayName("Should throw exception when extracting from malformed token")
        void shouldThrowExceptionForMalformedToken() {
            // Arrange
            String malformedToken = "not-a-jwt-token";

            // Act & Assert
            assertThrows(Exception.class, () -> jwtService.extractUsername(malformedToken));
        }

        @Test
        @DisplayName("Should extract username with mixed case")
        void shouldExtractUsernameWithMixedCase() {
            // Arrange
            String username = "User@Example.COM";
            String token = jwtService.generate(username, "CUSTOMER");

            // Act
            String extracted = jwtService.extractUsername(token);

            // Assert
            assertEquals(username, extracted);
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidationTests {

        @Test
        @DisplayName("Should validate correct token with matching username")
        void shouldValidateCorrectToken() {
            // Arrange
            String username = "user@example.com";
            String token = jwtService.generate(username, "CUSTOMER");

            // Act
            boolean isValid = jwtService.isTokenValid(token, username);

            // Assert
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should reject token with mismatched username")
        void shouldRejectTokenWithMismatchedUsername() {
            // Arrange
            String username1 = "user1@example.com";
            String username2 = "user2@example.com";
            String token = jwtService.generate(username1, "CUSTOMER");

            // Act
            boolean isValid = jwtService.isTokenValid(token, username2);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should reject invalid token")
        void shouldRejectInvalidToken() {
            // Arrange
            String invalidToken = "invalid.token.structure";
            String username = "user@example.com";

            // Act & Assert
            assertThrows(Exception.class, () -> jwtService.isTokenValid(invalidToken, username));
        }

        @Test
        @DisplayName("Should reject expired token")
        void shouldRejectExpiredToken() {
            // Arrange
            jwtService = new JwtService();
            ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000); // Already expired
            
            String username = "user@example.com";
            String token = jwtService.generate(username, "CUSTOMER");

            // Reset to normal expiration for validation
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION_TIME);

            // Act & Assert
            assertThrows(Exception.class, () -> jwtService.isTokenValid(token, username));
        }

        @Test
        @DisplayName("Should validate token case-sensitively for username")
        void shouldValidateTokenCaseSensitively() {
            // Arrange
            String username = "User@Example.COM";
            String differentCase = "user@example.com";
            String token = jwtService.generate(username, "CUSTOMER");

            // Act
            boolean isValid = jwtService.isTokenValid(token, differentCase);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should validate token with special characters in username")
        void shouldValidateTokenWithSpecialCharacters() {
            // Arrange
            String username = "user+tag@example.co.uk";
            String token = jwtService.generate(username, "CUSTOMER");

            // Act
            boolean isValid = jwtService.isTokenValid(token, username);

            // Assert
            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Token Expiration")
    class TokenExpirationTests {

        @Test
        @DisplayName("Should set token expiration time")
        void shouldSetTokenExpirationTime() {
            // Arrange
            String username = "user@example.com";

            // Act
            String token = jwtService.generate(username, "CUSTOMER");

            // Assert
            assertNotNull(token);
            assertFalse(token.isEmpty());
            // Token should be valid immediately after generation
            assertTrue(jwtService.isTokenValid(token, username));
        }

        @Test
        @DisplayName("Should generate tokens with far-future expiration")
        void shouldGenerateTokensWithValidExpiration() {
            // Arrange
            String username = "user@example.com";

            // Act
            String token = jwtService.generate(username, "CUSTOMER");

            // Assert
            assertTrue(jwtService.isTokenValid(token, username));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle username with numbers")
        void shouldHandleUsernameWithNumbers() {
            // Arrange
            String username = "user123@example.com";
            String role = "CUSTOMER";

            // Act
            String token = jwtService.generate(username, role);
            String extracted = jwtService.extractUsername(token);

            // Assert
            assertEquals(username, extracted);
        }

        @Test
        @DisplayName("Should handle very long username")
        void shouldHandleVeryLongUsername() {
            // Arrange
            String longUsername = "a".repeat(100) + "@example.com";
            String role = "CUSTOMER";

            // Act
            String token = jwtService.generate(longUsername, role);
            String extracted = jwtService.extractUsername(token);

            // Assert
            assertEquals(longUsername, extracted);
        }

        @Test
        @DisplayName("Should handle role with special characters")
        void shouldHandleRoleWithSpecialCharacters() {
            // Arrange
            String username = "user@example.com";
            String role = "ROLE_ADMIN_SUPER";

            // Act
            String token = jwtService.generate(username, role);

            // Assert
            assertNotNull(token);
            assertTrue(jwtService.isTokenValid(token, username));
        }

        @Test
        @DisplayName("Should generate consistent tokens for same input")
        void shouldGenerateTokensWithConsistentStructure() {
            // Arrange
            String username = "user@example.com";
            String role = "CUSTOMER";

            // Act
            String token1 = jwtService.generate(username, role);
            String token2 = jwtService.generate(username, role);

            // Assert
            // Both should be valid for the user even if content differs slightly due to timestamp
            assertTrue(jwtService.isTokenValid(token1, username));
            assertTrue(jwtService.isTokenValid(token2, username));
        }

        @Test
        @DisplayName("Should handle username with international characters")
        void shouldHandleInternationalCharacters() {
            // Arrange
            String username = "user.école@example.com";
            String role = "CUSTOMER";

            // Act
            String token = jwtService.generate(username, role);
            String extracted = jwtService.extractUsername(token);

            // Assert
            assertEquals(username, extracted);
        }
    }

    @Nested
    @DisplayName("Token Structure")
    class TokenStructureTests {

        @Test
        @DisplayName("Should generate token with three parts separated by dots")
        void shouldGenerateThreePartToken() {
            // Arrange
            String username = "user@example.com";

            // Act
            String token = jwtService.generate(username, "CUSTOMER");
            String[] parts = token.split("\\.");

            // Assert
            assertEquals(3, parts.length);
            assertNotNull(parts[0]); // Header
            assertNotNull(parts[1]); // Payload
            assertNotNull(parts[2]); // Signature
        }

        @Test
        @DisplayName("Should generate token with non-empty signature")
        void shouldGenerateTokenWithNonEmptySignature() {
            // Arrange
            String username = "user@example.com";

            // Act
            String token = jwtService.generate(username, "CUSTOMER");
            String[] parts = token.split("\\.");

            // Assert
            assertFalse(parts[2].isEmpty());
        }
    }
}
