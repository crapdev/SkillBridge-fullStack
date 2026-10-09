package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AdminUserService Tests")
class AdminUserServiceTest {

    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final PasswordHasherPort passwords = mock(PasswordHasherPort.class);
    private final AdminUserService service = new AdminUserService(users, passwords);

    private UserAccount user(Role role, AccountStatus status) {
        UserAccount u = new UserAccount(UUID.randomUUID(), "Ana", "ana@email.com", "hash", role, status, Instant.now());
        when(users.findById(u.id())).thenReturn(Optional.of(u));
        when(users.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));
        return u;
    }

    @Nested
    @DisplayName("Account Status Changes")
    class ChangeStatusTests {

        @Test
        @DisplayName("Should approve a pending provider")
        void shouldApprovePendingProvider() {
            UserAccount pending = user(Role.PROVIDER, AccountStatus.PENDING_APPROVAL);

            UserAccount result = service.changeStatus(pending.id(), AccountStatus.ACTIVE);

            assertEquals(AccountStatus.ACTIVE, result.status());
            assertEquals(pending.createdAt(), result.createdAt());
        }

        @Test
        @DisplayName("Should reject a pending provider")
        void shouldRejectPendingProvider() {
            UserAccount pending = user(Role.PROVIDER, AccountStatus.PENDING_APPROVAL);

            assertEquals(AccountStatus.REJECTED, service.changeStatus(pending.id(), AccountStatus.REJECTED).status());
        }

        @Test
        @DisplayName("Should not reject an account that is not pending")
        void shouldNotRejectActiveAccount() {
            UserAccount active = user(Role.PROVIDER, AccountStatus.ACTIVE);

            assertThrows(BusinessRuleException.class, () -> service.changeStatus(active.id(), AccountStatus.REJECTED));
            verify(users, never()).save(any());
        }

        @Test
        @DisplayName("Should deactivate and reactivate an account")
        void shouldDeactivateAndReactivate() {
            UserAccount active = user(Role.CUSTOMER, AccountStatus.ACTIVE);
            assertEquals(AccountStatus.INACTIVE, service.changeStatus(active.id(), AccountStatus.INACTIVE).status());

            UserAccount inactive = user(Role.CUSTOMER, AccountStatus.INACTIVE);
            assertEquals(AccountStatus.ACTIVE, service.changeStatus(inactive.id(), AccountStatus.ACTIVE).status());
        }

        @Test
        @DisplayName("Should not deactivate a pending provider (it must be approved or rejected)")
        void shouldNotDeactivatePendingProvider() {
            UserAccount pending = user(Role.PROVIDER, AccountStatus.PENDING_APPROVAL);

            assertThrows(BusinessRuleException.class, () -> service.changeStatus(pending.id(), AccountStatus.INACTIVE));
        }

        @Test
        @DisplayName("Should never move an account back to pending")
        void shouldNotMoveBackToPending() {
            UserAccount active = user(Role.PROVIDER, AccountStatus.ACTIVE);

            assertThrows(BusinessRuleException.class, () -> service.changeStatus(active.id(), AccountStatus.PENDING_APPROVAL));
        }

        @Test
        @DisplayName("Should return null for admins or missing users (404)")
        void shouldIgnoreAdminsAndMissingUsers() {
            UserAccount admin = user(Role.ADMIN, AccountStatus.ACTIVE);

            assertNull(service.changeStatus(admin.id(), AccountStatus.INACTIVE));
            assertNull(service.changeStatus(UUID.randomUUID(), AccountStatus.INACTIVE));
            verify(users, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Creation, Edition And Stats")
    class OtherTests {

        @Test
        @DisplayName("Should create provider accounts already active")
        void shouldCreateActiveProvider() {
            when(users.existsByEmail("nuevo@email.com")).thenReturn(false);
            when(passwords.encode("password123")).thenReturn("hash");
            when(users.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

            service.createUser("Nuevo", "nuevo@email.com", "password123", Role.PROVIDER);

            ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
            verify(users).save(saved.capture());
            assertEquals(AccountStatus.ACTIVE, saved.getValue().status());
        }

        @Test
        @DisplayName("Should keep the status when editing a user")
        void shouldKeepStatusOnUpdate() {
            UserAccount pending = user(Role.PROVIDER, AccountStatus.PENDING_APPROVAL);

            UserAccount result = service.updateUser(pending.id(), "Ana María", "ana@email.com", Role.PROVIDER);

            assertEquals(AccountStatus.PENDING_APPROVAL, result.status());
        }

        @Test
        @DisplayName("Should count active, pending and inactive accounts per role")
        void shouldIncludeStatusCountsInStats() {
            when(users.countByRoleAndStatusIn(eq(Role.PROVIDER), eq(EnumSet.of(AccountStatus.PENDING_APPROVAL)))).thenReturn(2L);
            when(users.countByRoleAndStatusIn(eq(Role.PROVIDER), eq(EnumSet.of(AccountStatus.INACTIVE, AccountStatus.REJECTED)))).thenReturn(1L);

            Map<String, Map<String, Long>> stats = service.getStats();

            assertEquals(2L, stats.get("providers").get("pending"));
            assertEquals(1L, stats.get("providers").get("inactive"));
            assertTrue(stats.get("customers").containsKey("active"));
        }
    }
}
