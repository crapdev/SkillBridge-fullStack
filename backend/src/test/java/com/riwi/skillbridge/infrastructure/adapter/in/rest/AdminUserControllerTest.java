package com.riwi.skillbridge.infrastructure.adapter.in.rest;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.in.AdminManageUsersUseCase;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminCreateUserRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminUpdateUserRequest;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
import com.riwi.skillbridge.infrastructure.security.RestAuthenticationEntryPoint;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;

@WebMvcTest(controllers = {AdminUserController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminManageUsersUseCase adminManageUsersUseCase;
    
    @MockitoBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    private UserAccount mockUser() {
        return new UserAccount(UUID.randomUUID(), "Test User", "test@example.com", "hash", Role.PROVIDER, Instant.now());
    }

    @Test
    void shouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/admin/users?role=PROVIDER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturn403WhenCustomer() throws Exception {
        mockMvc.perform(get("/api/admin/users?role=PROVIDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void shouldReturn403WhenProvider() throws Exception {
        mockMvc.perform(get("/api/admin/users?role=PROVIDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsers_ShouldReturnPage() throws Exception {
        UserAccount u = mockUser();
        when(adminManageUsersUseCase.listUsers(eq(Role.PROVIDER), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(u)));

        mockMvc.perform(get("/api/admin/users?role=PROVIDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(u.id().toString()))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsers_ShouldReturn400WhenRoleMissing() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsers_ShouldReturn400WhenRoleAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users?role=ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStats_ShouldReturnStats() throws Exception {
        when(adminManageUsersUseCase.getStats()).thenReturn(
                Map.of("providers", Map.of("total", 42L, "newThisMonth", 3L))
        );

        mockMvc.perform(get("/api/admin/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.total").value(42));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_ShouldReturnUser() throws Exception {
        UserAccount u = mockUser();
        when(adminManageUsersUseCase.getUserById(u.id())).thenReturn(u);

        mockMvc.perform(get("/api/admin/users/" + u.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(u.id().toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserById_ShouldReturn404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminManageUsersUseCase.getUserById(id)).thenReturn(null);

        mockMvc.perform(get("/api/admin/users/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_ShouldReturn201() throws Exception {
        AdminCreateUserRequest req = new AdminCreateUserRequest("New", "new@example.com", "password", Role.PROVIDER);
        UserAccount u = mockUser();
        when(adminManageUsersUseCase.createUser(any(), any(), any(), any())).thenReturn(u);

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(u.id().toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_ShouldReturn200() throws Exception {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest("Updated", "updated@example.com", Role.CUSTOMER);
        UserAccount u = mockUser();
        when(adminManageUsersUseCase.updateUser(any(), any(), any(), any())).thenReturn(u);

        mockMvc.perform(put("/api/admin/users/" + u.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(u.id().toString()));
    }
}
