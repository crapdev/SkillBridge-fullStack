package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ManageMyNotificationsUseCase;
import com.riwi.skillbridge.domain.model.Notification;
import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import com.riwi.skillbridge.infrastructure.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {NotificationController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageMyNotificationsUseCase useCase;

    @MockitoBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/notifications/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn403ForAdmin() throws Exception {
        mockMvc.perform(get("/api/notifications/me")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sofia@test.com", roles = "CUSTOMER")
    void shouldListMyNotifications() throws Exception {
        Notification notification = new Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.BOOKING_CREATED, "Reservaste Java Básico", Instant.now(), null);
        when(useCase.listMine("sofia@test.com", 10)).thenReturn(List.of(notification));

        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BOOKING_CREATED"))
                .andExpect(jsonPath("$[0].message").value("Reservaste Java Básico"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].recipientId").doesNotExist());
    }

    @Test
    @WithMockUser(username = "laura@test.com", roles = "PROVIDER")
    void shouldReturnUnreadCount() throws Exception {
        when(useCase.countUnread("laura@test.com")).thenReturn(2L);

        mockMvc.perform(get("/api/notifications/me/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @WithMockUser(username = "sofia@test.com", roles = "CUSTOMER")
    void shouldMarkAllAsRead() throws Exception {
        mockMvc.perform(patch("/api/notifications/me/read-all")).andExpect(status().isNoContent());

        verify(useCase).markAllRead("sofia@test.com");
    }
}
