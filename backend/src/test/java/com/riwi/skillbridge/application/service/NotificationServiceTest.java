package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private static final String EMAIL = "sofia@test.com";
    private final UUID userId = UUID.randomUUID();
    private NotificationRepositoryPort notifications;
    private UserAccountPort users;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepositoryPort.class);
        users = mock(UserAccountPort.class);
        service = new NotificationService(notifications, users);
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(userId));
    }

    @Test
    @DisplayName("Lista las notificaciones del usuario del token")
    void listMineUsesTheAuthenticatedUser() {
        service.listMine(EMAIL, 10);

        verify(notifications).findLatestByRecipient(userId, 10);
    }

    @Test
    @DisplayName("El límite se acota entre 1 y el máximo permitido")
    void listMineClampsTheLimit() {
        service.listMine(EMAIL, 1000);
        service.listMine(EMAIL, 0);

        verify(notifications).findLatestByRecipient(userId, NotificationService.MAX_LIMIT);
        verify(notifications).findLatestByRecipient(userId, 1);
    }

    @Test
    @DisplayName("Cuenta las no leídas del usuario del token")
    void countUnreadUsesTheAuthenticatedUser() {
        when(notifications.countUnread(userId)).thenReturn(3L);

        assertEquals(3L, service.countUnread(EMAIL));
    }

    @Test
    @DisplayName("Marca como leídas solo las del usuario del token")
    void markAllReadUsesTheAuthenticatedUser() {
        service.markAllRead(EMAIL);

        verify(notifications).markAllRead(eq(userId), any(Instant.class));
    }

    @Test
    @DisplayName("Un email sin usuario responde 404")
    void unknownUserFails() {
        when(users.findIdByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.countUnread("nadie@test.com"));
        verifyNoInteractions(notifications);
    }
}
